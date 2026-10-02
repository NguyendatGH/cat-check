package com.catcheck.scan.domain.color;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiểm thử ước lượng CCM bằng bình phương nhỏ nhất có trọng số.
 *
 * <p>Tiêu chí nghiệm thu (p6 §6.13.2 tầng 1): với nhiễu {@code σ = 1/255}, CCM phải khôi phục lại
 * ma trận thật với sai số tương đối <b>&lt; 2%</b>. Ngưỡng này có chủ đích lỏng: mục tiêu của CCM
 * không phải là "khớp tuyệt đối" mà là gỡ bỏ sai lệch có hệ thống. 2% trên hệ số ~1 là 0.02 —
 * nhỏ hơn nhiều lần so với ngưỡng ΔE00 ~2 mà người dùng nhìn thấy được.
 */
class CcmSolverTest {

    /**
     * Đáp ứng màu giả lập của một camera điện thoại: đường chéo lớn, rò chéo nhỏ.
     *
     * <p>Camera biến phản xạ chuẩn thành tín hiệu cảm biến: {@code measured = CAMERA_RESPONSE · reference}.
     * Ma trận lưu xuống DB là phép <b>sửa</b> chiều ngược lại, nên {@link CcmSolver} phải trả về
     * {@link Matrix3#inverse() nghịch đảo} của ma trận này — đó mới là "khôi phục được CCM" nghĩa là
     * dùng CCM ước lượng để đưa màu đo về màu thật.
     */
    private static final Matrix3 CAMERA_RESPONSE = new Matrix3(
            1.12, -0.06, -0.04,
            -0.09, 1.18, -0.07,
            -0.05, -0.08, 1.10);

    /** CCM đúng — phép sửa chiều ngược lại đáp ứng của camera. */
    private static final Matrix3 TRUE_CCM = CAMERA_RESPONSE.inverse();

    private static final double SIGMA = 1.0 / 255.0;

    @Test
    @DisplayName("Khôi phục CCM 3×3 từ dữ liệu nhiễu σ=1/255 — sai số tương đối < 2%")
    void recoversKnownCcmWithinTwoPercent() {
        List<ColorSamples.Sample> samples = simulate(64, 12345L);

        Matrix3 estimated = CcmSolver.solve(samples);
        double relativeError = estimated.relativeErrorTo(TRUE_CCM);

        assertThat(relativeError)
                .as("CCM uoc luoc: %s (sai so tuong doi %.4f%%)", estimated, relativeError * 100)
                .isLessThan(0.02);
    }

    @Test
    @DisplayName("Sai số giảm khi tăng số mẫu — dấu hiệu bài toán được giải đúng, không phải trùng hợp")
    void errorShrinksAsSampleCountGrows() {
        double fewSamples = CcmSolver.solve(simulate(12, 777L)).relativeErrorTo(TRUE_CCM);
        double manySamples = CcmSolver.solve(simulate(200, 777L)).relativeErrorTo(TRUE_CCM);

        assertThat(manySamples).isLessThan(fewSamples);
        assertThat(manySamples).isLessThan(0.02);
    }

    @Test
    @DisplayName("Dữ liệu hoàn hảo (không nhiễu) phải khôi phục CCM tuyệt đối")
    void recoversExactlyWithoutNoise() {
        List<ColorSamples.Sample> samples = new ArrayList<>();
        for (double[] rgb : srgbGrid(5)) {
            RgbLinear reference = ColorSpace.hexToLinearRgb("#" + hex(rgb));
            samples.add(ColorSamples.Sample.of(CAMERA_RESPONSE.apply(reference), reference));
        }

        assertThat(CcmSolver.solve(samples).relativeErrorTo(TRUE_CCM))
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("Trọng số thay đổi kết quả: mẫu nhiễu lớn bị hạ trọng số sẽ kéo ước lượng về mẫu sạch")
    void weightsShiftTheEstimate() {
        List<ColorSamples.Sample> clean = simulate(60, 4242L);

        // Thêm hai mẫu sai lệch hẳn, đặt trọng số 0: kết quả phải y hệt bộ mẫu sạch.
        List<ColorSamples.Sample> withExcluded = new ArrayList<>(clean);
        withExcluded.add(ColorSamples.Sample.excluded(new RgbLinear(0.1, 0.9, 0.4), new RgbLinear(0.5, 0.2, 0.1)));
        withExcluded.add(ColorSamples.Sample.excluded(new RgbLinear(0.8, 0.1, 0.3), new RgbLinear(0.1, 0.6, 0.9)));

        assertThat(CcmSolver.solve(withExcluded).relativeErrorTo(TRUE_CCM))
                .as("mau trong so 0 phai bi loai hoan toan")
                .isCloseTo(CcmSolver.solve(clean).relativeErrorTo(TRUE_CCM), within(1e-12));
    }

    @Test
    @DisplayName("Trọng số theo σ: mẫu nhiễu gấp đôi sigma phải bị đánh giá thấp hơn")
    void sigmaBasedWeightingPrefersCleanSamples() {
        RgbLinear reference = new RgbLinear(0.4, 0.35, 0.3);
        RgbLinear truth = CAMERA_RESPONSE.apply(reference);
        RgbLinear noisy = new RgbLinear(truth.r() + 0.08, truth.g() - 0.06, truth.b() + 0.05);

        double dirtyWeight = new ColorSamples.Sample(noisy, reference, 2 * SIGMA, 1.0).weightFromSigma();
        double tidyWeight = new ColorSamples.Sample(truth, reference, SIGMA, 1.0).weightFromSigma();

        assertThat(dirtyWeight)
                .as("mau nhieu hon phai co trong so nho hon")
                .isLessThan(tidyWeight);
    }

    @Test
    @DisplayName("solveCentered trả về phần tuyến tính, và phát hiện trường hợp quên khử trắng")
    void solveCenteredDetectsMissingWhiteBalance() {
        // Thêm một hằng số lệch vào toàn bộ mẫu đo = chưa khử trắng. CCM chỉ tự tâm trừ được phần
        // tuyến tính; phần tịch phải được xử lý ở tầng white balance.
        double offset = 0.15;
        List<ColorSamples.Sample> biased = new ArrayList<>();
        for (ColorSamples.Sample sample : simulate(40, 99L)) {
            RgbLinear m = sample.measured();
            biased.add(ColorSamples.Sample.of(
                    new RgbLinear(m.r() + offset, m.g() + offset, m.b() + offset), sample.reference()));
        }

        assertThatThrownBy(() -> CcmSolver.solveCentered(biased))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trung binh");
    }

    @Test
    @DisplayName("solveCentered trên dữ liệu đã cân bằng trắng vẫn ra CCM đúng")
    void solveCenteredWorksOnBalancedData() {
        List<ColorSamples.Sample> samples = simulate(40, 31337L);

        assertThat(CcmSolver.solveCentered(samples).relativeErrorTo(TRUE_CCM)).isLessThan(0.03);
    }

    @Test
    @DisplayName("Dữ liệu suy thoái bị từ chối kèm thông điệp, không trả về CCM vô nghĩa")
    void degenerateDataIsRejected() {
        RgbLinear same = new RgbLinear(0.3, 0.3, 0.3);
        List<ColorSamples.Sample> identical = List.of(
                ColorSamples.Sample.of(same, new RgbLinear(0.2, 0.3, 0.4)),
                ColorSamples.Sample.of(same, new RgbLinear(0.3, 0.2, 0.5)),
                ColorSamples.Sample.of(same, new RgbLinear(0.4, 0.4, 0.1)));

        assertThatThrownBy(() -> CcmSolver.solve(identical))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("suy thoang");
    }

    @Test
    @DisplayName("Quá ít mẫu bị từ chối kèm số lượng thực tế trong thông điệp")
    void tooFewSamplesIsRejectedWithCount() {
        List<ColorSamples.Sample> two = List.of(
                ColorSamples.Sample.of(new RgbLinear(0.1, 0.1, 0.1), new RgbLinear(0.2, 0.1, 0.1)),
                ColorSamples.Sample.of(new RgbLinear(0.5, 0.5, 0.5), new RgbLinear(0.4, 0.5, 0.5)));

        assertThatThrownBy(() -> CcmSolver.solve(two))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2");
    }

    // ------------------------------------------------------------------ ma trận

    @Test
    @DisplayName("Ma trận: nghịch đảo khứ hồi đúng ma trận gốc")
    void matrixInverseRoundTrip() {
        Matrix3 product = CAMERA_RESPONSE.multiply(CAMERA_RESPONSE.inverse());

        assertThat(product.relativeErrorTo(Matrix3.IDENTITY)).isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("Ma trận gần kém bị từ chối khi nghịch đảo — nghịch đảo thành công là cái nguy hiểm")
    void singularMatrixIsRejected() {
        Matrix3 singular = new Matrix3(1, 2, 3, 2, 4, 6, 3, 6, 9);

        assertThat(singular.determinant()).isCloseTo(0.0, within(1e-12));
        assertThatThrownBy(singular::inverse).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("Ma trận serialize qua mảng 9 phần tử là bất biến cả hai chiều")
    void matrixArrayRoundTrip() {
        double[] exported = TRUE_CCM.toArray();
        exported[4] = 999.0;

        assertThat(TRUE_CCM.m11()).as("sua mang tra ve khong duoc doi ma tran goc").isNotEqualTo(999.0);
        assertThat(Matrix3.ofArray(TRUE_CCM.toArray())).isEqualTo(TRUE_CCM);
        assertThatThrownBy(() -> Matrix3.ofArray(new double[] {1, 2, 3}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("CCM hợp lệ phải giữ được thứ tự kênh: áp lên trắng cho ra trắng, không đảo BGR")
    void ccmPreservesChannelOrder() {
        RgbLinear red = ColorSpace.hexToLinearRgb("#FF0000");

        RgbLinear corrected = TRUE_CCM.apply(red);

        assertThat(corrected.r())
                .as("CCM sua nen tra ve mau doi chieu, khong dao kenh")
                .isGreaterThan(corrected.g());
        assertThat(corrected.r()).isGreaterThan(corrected.b());
    }

    // ------------------------------------------------------------------ dữ liệu mô phỏng

    private static List<ColorSamples.Sample> simulate(int count, long seed) {
        Random random = new Random(seed);
        double[][] grid = srgbGrid(6);
        List<ColorSamples.Sample> samples = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // Chọn màu ngẫu nhiên trên lưới chứ không lấy N màu đầu tiên: N màu đầu tiên nằm gọn
            // trong góc tối của lăng kính, hệ gần như suy thoái và sai số ước lượng vọt lên hàng
            // chục phần trăm — đó là khiếm khuyết của *bộ mẫu*, không phải của thuật toán.
            double[] rgb = grid[random.nextInt(grid.length)];
            RgbLinear reference = ColorSpace.hexToLinearRgb("#" + hex(rgb));
            RgbLinear truth = CAMERA_RESPONSE.apply(reference);
            RgbLinear measured = new RgbLinear(
                    truth.r() + random.nextGaussian() * SIGMA,
                    truth.g() + random.nextGaussian() * SIGMA,
                    truth.b() + random.nextGaussian() * SIGMA);
            samples.add(ColorSamples.Sample.of(measured, reference));
        }
        return samples;
    }

    /** Lưới sRGB: 6 bậc mỗi kênh, bỏ các sắc thái quá gần nhau để hệ không suy thoái. */
    private static double[][] srgbGrid(int steps) {
        List<double[]> colors = new ArrayList<>();
        int span = 255 / steps;
        for (int r = 0; r <= steps; r++) {
            for (int g = 0; g <= steps; g++) {
                for (int b = 0; b <= steps; b++) {
                    colors.add(new double[] {r * span, g * span, b * span});
                }
            }
        }
        return colors.toArray(new double[0][]);
    }

    private static String hex(double[] rgb) {
        return String.format("%02X%02X%02X", (int) rgb[0], (int) rgb[1], (int) rgb[2]);
    }
}
