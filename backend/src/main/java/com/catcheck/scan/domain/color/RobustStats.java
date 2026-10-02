package com.catcheck.scan.domain.color;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Thống kê robust cho tập màu của các hạt chỉ thị — bước S7 (p6 §6.5.1).
 *
 * <h2>Vì sao không dùng trung bình</h2>
 * <p>Một hạt cát bị đọc sai (một pixel sáng lóa, một hạt đất vụn) sẽ kéo trung bình đi xa. Với
 * chỉ số pH, sai số một lần đọc hỏng lại thành "mèo có vấn đề" — loại phân loại sai tệ nhất về mặt
 * niềm tin. Trung vị và MAD không đổi giá trị khi có một phần dữ liệu hỏng, nên đây là lựa chọn
 * mặc định bắt buộc của hệ thống, không phải tối ưu hoá.
 *
 * <h2>Tham số (p6 §6.5.1 S7, nghiệm thu tại p6 §6.13.2)</h2>
 * <ul>
 *   <li>{@code outlierK = 2.5} theo đơn vị MAD để loại outlier</li>
 *   <li>{@code trim = 0.10} — cắt bỏ 10% mỗi đuôi trước khi lấy trung vị, khi mẫu đủ lớn</li>
 *   <li>{@code minBlobs = 4} — dưới ngưỡng này thống kê không đáng tin, giữ lại toàn bộ mẫu</li>
 * </ul>
 */
public final class RobustStats {

    /** Hệ số loại outlier tính theo MAD (p6 S7). */
    public static final double OUTLIER_K = 2.5;

    /** Tỉ lệ cắt mỗi đuôi khi mẫu đủ lớn. */
    public static final double TRIM = 0.10;

    /** Dưới ngưỡng này không cắt, không loại — thống kê không đủ dữ liệu để kết luận. */
    public static final int MIN_BLOBS = 4;

    private RobustStats() {
        throw new AssertionError("RobustStats la lop tien ich, khong instantiate");
    }

    /** Kết quả thống kê S7. */
    public record Result(
            Lab median,
            int blobCount,
            int droppedCount,
            double spreadDeltaE00,
            boolean fewBlobs,
            boolean mixedColors,
            double madDistance) {

        /** Ngưỡng {@code spreadΔE00} coi như hạt lẫn nhiều màu (nghiên cứu S8: > 6 là đáng ngờ). */
        public boolean looksMixed() {
            return mixedColors;
        }
    }

    /**
     * Chạy S7 cho tập Lab của các hạt.
     *
     * <p>Đúng thứ tự đặc tả (nghiên cứu color-pipeline S7): median từng kênh để tìm tâm → khoảng cách
     * ΔE00 tới tâm → loại theo {@code median(d) + 2.5·madD} → <b>trimmed mean</b> trên tập còn lại.
     *
     * <p>Vì sao bước tổng hợp là trimmed mean chứ không phải median: median đã dùng một lần để tìm
     * tâm và loại outlier, dùng lần hai thì thành toán bộ nhận dạng số một. Trimmed mean vẫn kháng
     * outlier (đã cắt 10% mỗi đuôi) nhưng vẫn dùng được thông tin của các mẫu ở giữa.
     *
     * @param samples   Lab từng hạt
     * @param mixedFlag ngưỡng spread để cờ {@code MIXED_COLORS}
     */
    public static Result analyze(List<Lab> samples, double mixedFlag) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Can it nhat mot mau de thong ke");
        }

        Lab center0 = medianPerChannel(samples);
        double[] distances = distancesTo(samples, center0);
        double medianDistance = median(distances);
        double madD = 1.4826 * medianAbsoluteDeviation(distances, medianDistance);
        double threshold = medianDistance + OUTLIER_K * madD;

        List<Lab> kept = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            if (distances[i] <= threshold) {
                kept.add(samples.get(i));
            }
        }

        boolean fewBlobs = kept.size() < MIN_BLOBS;
        Lab median = fewBlobs ? center0 : trimmedMeanPerChannel(kept, TRIM);

        // spreadΔE00 = trung vị khoảng cách tới labMedian: đo "các hạt đồng màu thế nào", nên nó ổn
        // định trước outlier thay vì phản ánh biên độ.
        double spread = median(distancesTo(kept, median));

        return new Result(median, kept.size(), samples.size() - kept.size(), spread, fewBlobs,
                spread > mixedFlag, madD);
    }

    /** S7 với ngưỡng cờ trộn màu mặc định của hệ thống. */
    public static Result analyze(List<Lab> samples) {
        return analyze(samples, 6.0);
    }

    private static double[] distancesTo(List<Lab> samples, Lab center) {
        double[] distances = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            distances[i] = DeltaE2000.deltaE(samples.get(i), center, DeltaE2000Params.CAT_CHECK);
        }
        return distances;
    }

    private static double medianAbsoluteDeviation(double[] values, double center) {
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - center);
        }
        return median(deviations);
    }

    /**
     * Loại outlier theo đúng quy tắc S7: giữ hạt có {@code d[j] ≤ median(d) + k·madD}.
     *
     * <p>Trả về <b>toàn bộ</b> mẫu nếu việc lọc làm mất quá nhiều dữ liệu — mất 2/3 mẫu thì phép
     * tổng hợp còn lại đại diện cho một tập rỗng, tệ hơn là giữ hết và để {@code FEW_BLOBS} cảnh báo.
     */
    public static List<Lab> trimOutliers(List<Lab> samples) {
        if (samples.size() < MIN_BLOBS) {
            return List.copyOf(samples);
        }
        Lab center0 = medianPerChannel(samples);
        double[] distances = distancesTo(samples, center0);
        double medianDistance = median(distances);
        double threshold = medianDistance
                + OUTLIER_K * (1.4826 * medianAbsoluteDeviation(distances, medianDistance));

        List<Lab> kept = new ArrayList<>(samples.size());
        for (int i = 0; i < samples.size(); i++) {
            if (distances[i] <= threshold) {
                kept.add(samples.get(i));
            }
        }
        return kept.size() < MIN_BLOBS ? List.copyOf(samples) : List.copyOf(kept);
    }

    /**
     * Trung bình đã cắt {@code trim} mỗi đuôi, theo từng kênh độc lập.
     *
     * <p>Cắt theo kênh chứ không theo quả cầu Lab: hai hạt có cùng {@code L*} nhưng khác {@code a*}
     * là hai hạt khác nhau, và cắt chung sẽ loại nhầm.
     */
    public static Lab trimmedMeanPerChannel(List<Lab> samples, double trim) {
        double[] l = new double[samples.size()];
        double[] a = new double[samples.size()];
        double[] b = new double[samples.size()];
        for (int i = 0; i < samples.size(); i++) {
            l[i] = samples.get(i).l();
            a[i] = samples.get(i).a();
            b[i] = samples.get(i).b();
        }
        return new Lab(trimmedMean(l, trim), trimmedMean(a, trim), trimmedMean(b, trim));
    }

    /** Trung vị theo từng kênh L, a, b độc lập — đây là {@code lab_median} của S7. */
    public static Lab medianPerChannel(Collection<Lab> samples) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("Khong co mau nao de tinh trung vi");
        }
        double[] l = new double[samples.size()];
        double[] a = new double[samples.size()];
        double[] b = new double[samples.size()];
        int i = 0;
        for (Lab sample : samples) {
            l[i] = sample.l();
            a[i] = sample.a();
            b[i] = sample.b();
            i++;
        }
        return new Lab(median(l), median(a), median(b));
    }

    /** MAD — trung vị của các khoảng cách tuyệt đối tới trung vị. */
    public static double mad(Collection<Lab> samples, java.util.function.ToDoubleFunction<Lab> channel) {
        Lab center = medianPerChannel(samples);
        double[] deviations = new double[samples.size()];
        int i = 0;
        for (Lab sample : samples) {
            deviations[i] = Math.abs(channel.applyAsDouble(sample) - channel.applyAsDouble(center));
            i++;
        }
        return median(deviations);
    }

    /** MAD của khoảng cách ΔE00: 1.4826 chỉ để ước lượng độ lệch chuẩn khi phân phối chuẩn. */
    public static double distanceMad(List<Lab> samples, Lab center) {
        double[] distances = distancesTo(samples, center);
        return 1.4826 * medianAbsoluteDeviation(distances, median(distances));
    }

    /**
     * Trung vị có cắt đuôi {@link #TRIM} khi mẫu đủ lớn.
     *
     * <p>Khác {@link #medianPerChannel} ở chỗ này là <em>phân phối</em> của một kênh, không phải
     * phân phối của các hạt. Dùng khi ước lượng đường cong tuyến tính hoá từ thang xám của thẻ.
     */
    public static double trimmedMedian(double[] values, double trim) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Mang rong khong duoc null hoac rong");
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int cut = (int) Math.floor(sorted.length * trim);
        if (sorted.length - 2 * cut < 1) {
            cut = 0;
        }
        return median(java.util.Arrays.copyOfRange(sorted, cut, sorted.length - cut));
    }

    /** Trung bình của phần giữa sau khi cắt {@code trim} mỗi đuôi. */
    public static double trimmedMean(double[] values, double trim) {
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int cut = (int) Math.floor(sorted.length * trim);
        if (sorted.length - 2 * cut < 1) {
            cut = 0;
        }
        double sum = 0.0;
        int n = sorted.length - 2 * cut;
        for (int i = cut; i < sorted.length - cut; i++) {
            sum += sorted[i];
        }
        return sum / n;
    }

    /** Trung vị: trung bình hai giá trị giữa khi số phần tử chẵn. */
    public static double median(double[] values) {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Mang rong khong duoc null hoac rong");
        }
        double[] sorted = values.clone();
        java.util.Arrays.sort(sorted);
        int n = sorted.length;
        return n % 2 == 1 ? sorted[n / 2] : (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
    }
}
