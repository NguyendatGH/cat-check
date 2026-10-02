package com.catcheck.scan.domain.color;

import java.util.List;

/**
 * Ước lượng <b>CCM — Color Correction Matrix</b> 3×3 bằng bình phương nhỏ nhất có trọng số
 * (weighted least squares), giải trên phép tăng dần tuyến tính có chọn trục (partial pivoting).
 *
 * <h2>CCM dùng để làm gì</h2>
 * <p>Cùng một màu thật, mỗi camera cho ra một màu khác nhau — vừa vì bộ lọc Bayer, vừa vì
 * ống kính, vừa vì đường cong gamma của ISP. Đó là sai lệch <em>có hệ thống</em>, và chỉ sửa được
 * bằng một ma trận tuyến tính 3×3. Không sửa nó thì mọi giá trị pH đo được đều mang hệ thống lệch
 * riêng cho từng đời máy, tức mọi mốc phân loại đều sai theo cách không thể dự đoán trước.
 *
 * <h2>Vì sao phải có trọng số</h2>
 * <p>Trong không gian <b>linear</b>, nhiễu quang học có phương sai <em>tăng theo cường độ</em>
 * (nhiễu photon: ở vùng tối, sai số tuyệt đối lại lớn). Nếu tất cả các mẫu được coi ngang nhau,
 * các mẫu tối sẽ chi phối phép khớp và CCM sẽ bị kéo lệch. Vì vậy mỗi mẫu mang một trọng số
 * {@code w = 1/σ²} cùng một {@link ColorSamples.Sample#sigma() sigma}, cho phép phép khớp coi
 * trọng mẫu sáng đáng tin hơn mẫu tối, hoặc loại mẫu rác bằng trọng số 0.
 *
 * <h2>Vì sao giải bằng phương pháp bình phương nhỏ nhất chứ không "3 điểm là đủ"</h2>
 * <p>Trong nghiên cứu, 3 patch nền tảng đôi khi cho đủ 9 phương trình. Nhưng phương pháp đó
 * <em>cực kỳ nhạy với nhiễu</em> và vỡ tan khi ba patch gần như trùng màu — tức đúng trường hợp
 * thẻ tham chiếu CatCheck chỉ có vài ô màu. Ước lượng bằng tất cả patch sẵn có + kiểm tra hậu kiểm
 * là lựa chọn bền vững hơn nhiều.
 */
public final class CcmSolver {

    /** Ngưỡng xác định thứ hạng: hệ số nhân bị chuẩn hoá, dưới ngưỡng này coi như hệ bậc ba kém. */
    private static final double RANKING_TOLERANCE = 1e-9;

    /**
     * Ngưỡng phần tịch còn sót lại sau khi tâm trừ. 0.02 trong linear ≈ 5/255 — tức nhỏ hơn nhiều
     * so với ΔE00 phân biệt được (~2), nhưng đủ lớn để bắt trường hợp quên khử trắng.
     */
    private static final double WHITE_BALANCE_TOLERANCE = 0.02;

    private CcmSolver() {
        throw new AssertionError("CcmSolver la lop tien ich, khong instantiate");
    }

    /**
     * Ước lượng CCM từ các cặp (mẫu đo, giá trị tham chiếu) bằng WLS.
     *
     * @param samples cặp mẫu; mỗi mẫu là linear RGB đo được và linear RGB tham chiếu
     * @return ma trận M sao cho {@code M · measured ≈ reference}
     * @throws IllegalArgumentException nếu thiếu mẫu cho hệ 9 ẩn số, hệ suy thoái, hoặc mẫu vô
     *                                  nghĩa (NaN, trọng số âm)
     */
    public static Matrix3 solve(List<ColorSamples.Sample> samples) {
        if (samples == null || samples.size() < 3) {
            throw new IllegalArgumentException("Can it nhat 3 mau de uoc luong CCM 3x3, nhan duoc "
                    + (samples == null ? 0 : samples.size()));
        }

        // Hệ bình phương nhỏ nhất: AᵀWA với A = vec(measured) (3×N) nên AᵀWA là 3×3, giống nhau
        // cho cả ba kênh đầu ra. Vế phải khác nhau theo từng kênh: b_j = Σ w·measured·reference_j.
        // Vì vậy dùng một ma trận hệ số 3×3 và ba vế phải — tức 3 hệ cùng hệ số, giải chung một lần.
        double[][] normal = new double[3][6];
        int used = 0;

        for (ColorSamples.Sample sample : samples) {
            if (sample.weight() <= 0.0) {
                continue;
            }
            used++;
            double w = sample.weight();
            double[] measured = {sample.measured().r(), sample.measured().g(), sample.measured().b()};
            double[] reference = {sample.reference().r(), sample.reference().g(), sample.reference().b()};

            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    normal[row][col] += w * measured[row] * measured[col];
                }
                for (int channel = 0; channel < 3; channel++) {
                    normal[row][3 + channel] += w * measured[row] * reference[channel];
                }
            }
        }

        if (used < 3) {
            throw new IllegalArgumentException("So mau co trong so > 0 phai >= 3, nhan duoc " + used);
        }

        // Giải chung: phân rã theo hàng một lần, khử lùi cho cả ba vế phải.
        eliminate(normal);
        double[] red = backSubstitute(normal, 3);
        double[] green = backSubstitute(normal, 4);
        double[] blue = backSubstitute(normal, 5);

        return new Matrix3(
                red[0], red[1], red[2],
                green[0], green[1], green[2],
                blue[0], blue[1], blue[2]);
    }

    /**
     * Ước lượng CCM trên dữ liệu đã <b>tâm trừ trung bình</b> (centered) — bản dùng cho pipeline thật.
     *
     * <p>Lý do: hệ 9 ẩn số ở {@link #solve} <b>không xác định duy nhất</b>. Với bất kỳ vector phẳng
     * {@code d}, ma trận {@code M} và {@code M + d·eᵀ} cho ra cùng dự đoán trên mọi ảnh <em>không</em>
     * bão hoà (vì {@code d·(1,1,1) = 0}). Chừng nào còn ánh sáng trung tính chưa được khử, sai số
     * của phép tâm trừ sẽ bị hấp thụ vào CCM và khiến giá trị Lab thu được lệch theo cường độ.
     * Phần tịch còn lại {@code refMean − M·measuredMean} là một vector, <b>không</b> phải phần của
     * CCM 3×3: nó chính là việc khử trắng, và do {@link WhiteBalanceSolver} đảm nhiệm trước bước này.
     * Ở đây chỉ trả về phần tuyến tính thuần.
     */
    public static Matrix3 solveCentered(List<ColorSamples.Sample> samples) {
        RgbLinear measuredMean = mean(samples, true);
        RgbLinear referenceMean = mean(samples, false);

        var shifted = new java.util.ArrayList<ColorSamples.Sample>(samples.size());
        for (ColorSamples.Sample sample : samples) {
            shifted.add(new ColorSamples.Sample(
                    new RgbLinear(sample.measured().r() - measuredMean.r(),
                            sample.measured().g() - measuredMean.g(),
                            sample.measured().b() - measuredMean.b()),
                    new RgbLinear(sample.reference().r() - referenceMean.r(),
                            sample.reference().g() - referenceMean.g(),
                            sample.reference().b() - referenceMean.b()),
                    sample.sigma(),
                    sample.weight()));
        }

        Matrix3 linear = solve(shifted);
        // Kiểm tra phần tịch phải nhỏ; nếu lớn thì white balance chưa làm việc của nó và CCM sẽ
        // phải bù bằng thành phần ngoài gamut — dấu hiệu sớm của việc hiệu chuẩn sai.
        double[] residual = linear.apply(new double[] {measuredMean.r(), measuredMean.g(), measuredMean.b()});
        double offsetMagnitude = Math.hypot(
                Math.hypot(residual[0] - referenceMean.r(),
                        residual[1] - referenceMean.g()),
                residual[2] - referenceMean.b());
        if (offsetMagnitude > WHITE_BALANCE_TOLERANCE) {
            throw new IllegalStateException(String.format(
                    "Phan lech giua mau trung binh con lai %.4f (> %.4f) — can khu trang truoc khi uoc luong CCM",
                    offsetMagnitude, WHITE_BALANCE_TOLERANCE));
        }
        return linear;
    }

    private static RgbLinear mean(List<ColorSamples.Sample> samples, boolean measured) {
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        int n = 0;
        for (ColorSamples.Sample sample : samples) {
            RgbLinear v = measured ? sample.measured() : sample.reference();
            sr += v.r();
            sg += v.g();
            sb += v.b();
            n++;
        }
        if (n == 0) {
            throw new IllegalArgumentException("Khong co mau nao de tinh trung binh");
        }
        return new RgbLinear(sr / n, sg / n, sb / n);
    }

    /**
     * Khử tam giác trên hệ 3×3 với ba vế phải, bằng Gauss có chọn trục cột.
     *
     * <p>Chọn trục là bắt buộc ở đây: các cột của {@code AᵀWA} là bình phương giá trị linear, nên
     * chúng lệch vài bậc độ lớn giữa mẫu sáng và mẫu tối. Không chọn trục, phép khử chuẩn hoá có thể
     * làm mất chính xác ở đúng những ma trận tương phốn.
     */
    private static void eliminate(double[][] augmented) {
        for (int pivot = 0; pivot < 3; pivot++) {
            int best = pivot;
            for (int row = pivot + 1; row < 3; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[best][pivot])) {
                    best = row;
                }
            }
            if (Math.abs(augmented[best][pivot]) < RANKING_TOLERANCE) {
                throw new IllegalArgumentException(
                        "He phuong trinh CCM suy thoang (cot " + pivot + " khong doc lap) — "
                                + "du lieu mau khong du phu tap khac nhau");
            }
            double[] swap = augmented[pivot];
            augmented[pivot] = augmented[best];
            augmented[best] = swap;

            for (int row = pivot + 1; row < 3; row++) {
                double factor = augmented[row][pivot] / augmented[pivot][pivot];
                for (int col = pivot; col < augmented[pivot].length; col++) {
                    augmented[row][col] -= factor * augmented[pivot][col];
                }
            }
        }
    }

    /** Khử lùi cho một vế phải đã khử tam giác trên. */
    private static double[] backSubstitute(double[][] augmented, int rhsColumn) {
        double[] x = new double[3];
        for (int row = 2; row >= 0; row--) {
            double sum = augmented[row][rhsColumn];
            for (int col = row + 1; col < 3; col++) {
                sum -= augmented[row][col] * x[col];
            }
            x[row] = sum / augmented[row][row];
        }
        return x;
    }
}
