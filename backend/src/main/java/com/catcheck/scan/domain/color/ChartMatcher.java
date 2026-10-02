package com.catcheck.scan.domain.color;

import java.util.List;
import java.util.Optional;

/**
 * Bước <b>S8 — so khớp bảng màu</b> (p6 §6.5.1 S8, nghiên cứu color-pipeline S8).
 *
 * <h2>Ý tưởng trung tâm: nội suy trên đường cong Lab</h2>
 * <p>Các mức pH trên thẻ là <b>điểm</b>, không phải khoảng. Người dùng không chọn "mức 6,5", họ
 * chụp một màu nằm <em>giữa</em> hai mức. Nên phép khớp đúng là: dự án màu đo lên từng đoạn nối
 * hai mức liền kề, rồi nội suy <b>theo khoảng cách ΔE00</b> chứ không theo khoảng cách pH — vì ΔE00
 * mới là thứ camera thực sự đo, và tỉ lệ "1 ΔE00 ứng với bao nhiêu pH" là khác nhau ở từng vùng màu.
 *
 * <h2>Vì sao dò khiếm đoạn thay vì chỉ đoạn quanh mức gần nhất</h2>
 * <p>Cách "lấy mức gần nhất rồi nội suy với đoạn kề nó" <b>sai</b> khoảng một nửa các ca: nếu mức
 * gần nhất là 6.6, đoạn được chọn là 6.6→7.5, trong khi màu thật lại nằm giữa 6.3 và 6.6. pH trả
 * về lệch sang phải — và lệch kiểu này <b>không</b> bị bắt bởi bất kỳ test nào chỉ kiểm tra "pH nằm
 * trong dải bảng". Vì vậy ở đây dự án lên <b>tất cả</b> đoạn (bảng màu chỉ vài chục mức, chi phí không
 * đáng kể) và chọn đoạn có phần dư ΔE00 nhỏ nhất.
 *
 * <h2>ON_CARD_RELATIVE — vì sao bắt buộc</h2>
 * <p>Khi ảnh có thẻ, mẫu và ô tham chiếu cùng chịu một chuỗi biến đổi (ánh sáng, ống kính, tone
 * mapping của ISP). Phần lớn sai số vì vậy bị <b>triệt tiêu</b> khi so trực tiếp mẫu với ô cùng
 * nằm trong ảnh. Bảng Lab trong DB chỉ còn vai trò dự phòng và đối chiếu chéo — dùng nó làm nguồn
 * chính là đánh mất chính xác một cách âm thầm.
 */
public final class ChartMatcher {

    /** Thang ánh xạ ΔE00 → phần trăm khớp (nghiên cứu S8: {@code matchScaleDE = 15}). */
    public static final double MATCH_SCALE_DE = 15.0;

    /** Ngưỡng ΔE00 tối thiểu coi là nằm ngoài dải chỉ thị (p6 S8). */
    private static final double OUT_OF_RANGE_TOLERANCE = 4.0;

    /** Ngưỡng lệch pH giữa hai chế độ khớp để gắn cờ (p6 S8). */
    public static final double CARD_CHART_MISMATCH_PH = 0.3;

    private ChartMatcher() {
        throw new AssertionError("ChartMatcher la lop tien inch, khong instantiate");
    }

    /**
     * Kết quả khớp.
     *
     * @param phEstimate      pH ước lượng
     * @param deltaEMin       ΔE00 nhỏ nhất tới mức khớp
     * @param matchedPointId  mức khớp gần nhất
     * @param interpolatedT   vị trí nội suy trong đoạn (0 = mức dưới, 1 = mức trên)
     * @param lowerPoint      mức dưới của đoạn được chọn
     * @param upperPoint      mức trên của đoạn được chọn
     * @param matchPercent    phần trăm khớp hiển thị (85–95 là bình thường, không phải 98)
     * @param outOfChartRange màu nằm ngoài dải chỉ thị
     */
    public record Match(
            double phEstimate,
            double deltaEMin,
            String matchedPointId,
            double interpolatedT,
            PhChartPoint lowerPoint,
            PhChartPoint upperPoint,
            double matchPercent,
            boolean outOfChartRange) {
    }

    /**
     * Khớp mẫu đo với bảng màu.
     *
     * @param sample        màu đã hiệu chuẩn (Lab D65/2°)
     * @param chart         bảng màu ACTIVE
     * @param onCardPatches màu đo được của <b>từng ô</b> trên thẻ trong cùng ảnh, cùng thứ tự với
     *                      {@code chart.points()}; {@code null} hoặc rỗng nghĩa là không có thẻ và
     *                      phải dùng bảng DB
     */
    public static Match match(Lab sample, PhChart chart, List<Lab> onCardPatches) {
        if (sample == null) {
            throw new IllegalArgumentException("Can mau do de khop");
        }
        if (chart == null || chart.points().size() < 2) {
            throw new IllegalArgumentException("Bang mau can it nhat 2 muc de noi suy pH");
        }
        boolean relative = onCardPatches != null && onCardPatches.size() == chart.points().size();
        if (onCardPatches != null && !relative && !onCardPatches.isEmpty()) {
            throw new IllegalArgumentException("So o tren the khong khop so muc cua bang mau: "
                    + onCardPatches.size() + " vs " + chart.points().size());
        }
        return relative ? matchOnCard(sample, chart, onCardPatches) : matchOnDatabase(sample, chart);
    }

    /** Chế độ không có thẻ: tham chiếu là Lab trong DB. */
    private static Match matchOnDatabase(Lab sample, PhChart chart) {
        DeltaE2000Params params = chart.deltaEParams();
        List<PhChartPoint> points = chart.points();
        List<Lab> references = points.stream().map(PhChartPoint::lab).toList();
        return project(sample, chart, references, params, outOfRangeCheck(sample, points, references, params));
    }

    /**
     * Chế độ {@code ON_CARD_RELATIVE}: tham chiếu là ô cùng ảnh.
     *
     * <p>Không so sánh mẫu với ô thẻ một cách trực tiếp rồi kết luận — mà <b>dự án</b> mẫu lên đường
     * nối các ô trên thẻ. Cách đó giữ được hình học của bảng màu (thang pH vẫn là thang pH) trong khi
     * mọi sai lệch của chuỗi hiệu chuẩn bị loại bỏ cùng nhau.
     */
    private static Match matchOnCard(Lab sample, PhChart chart, List<Lab> onCardPatches) {
        return project(sample, chart, onCardPatches, chart.deltaEParams(), false);
    }

    /**
     * Dự án mẫu lên đường cong nối các điểm tham chiếu, chọn đoạn có phần dư nhỏ nhất.
     *
     * @param outOfRange kết quả kiểm tra ngoài dải (chỉ có ý nghĩa ở chế độ bảng DB)
     */
    private static Match project(Lab sample, PhChart chart, List<Lab> references,
                                 DeltaE2000Params params, boolean outOfRange) {
        List<PhChartPoint> points = chart.points();
        int referencesCount = references.size();

        int bestLower = 0;
        double bestResidual = Double.MAX_VALUE;
        double bestT = 0.0;
        int nearestIndex = 0;
        double nearestDistance = Double.MAX_VALUE;

        for (int i = 0; i < referencesCount; i++) {
            double distance = DeltaE2000.deltaE(sample, references.get(i), params);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestIndex = i;
            }
            if (i >= referencesCount - 1) {
                continue;
            }

            double segmentDeltaE = DeltaE2000.deltaE(references.get(i), references.get(i + 1), params);
            double t = segmentDeltaE < 1e-9
                    ? 0.0
                    : clamp01(DeltaE2000.deltaE(sample, references.get(i), params) / segmentDeltaE);
            Lab projected = interpolateLab(references.get(i), references.get(i + 1), t);
            double residual = DeltaE2000.deltaE(sample, projected, params);

            if (residual < bestResidual) {
                bestResidual = residual;
                bestLower = i;
                bestT = t;
            }
        }

        PhChartPoint lower = points.get(bestLower);
        PhChartPoint upper = points.get(bestLower + 1);
        PhChartPoint nearest = points.get(nearestIndex);

        double ph = round1(lower.phValue() + bestT * (upper.phValue() - lower.phValue()));
        ph = Math.max(chart.minPh(), Math.min(chart.maxPh(), ph));

        return new Match(ph, nearestDistance, nearest.id(), round2(bestT), lower, upper,
                matchPercent(nearestDistance), outOfRange);
    }

    /**
     * Nội suy Lab trong đoạn theo tỉ lệ {@code t} — dùng để tính phần dư khi dự án.
     *
     * <p>Nội suy tuyến tính trong không gian Lab (không phải trong XYZ hay XYZ sau CCM): Lab là
     * không gian gần như thống nhất về thị giác, và giữa hai ô cùng thẻ màu thay đổi một chiều, nội suy
     * tuyến tính trong Lab là xấp xỉ tốt nhất với sai số nhỏ hơn nhiều lần so với nội suy trong XYZ.
     */
    private static Lab interpolateLab(Lab from, Lab to, double t) {
        return new Lab(
                from.l() + t * (to.l() - from.l()),
                from.a() + t * (to.a() - from.a()),
                from.b() + t * (to.b() - from.b()));
    }

    /**
     * Màu nằm ngoài dải chỉ thị: bị kẹp ở đầu mút <b>và</b> ΔE00 vượt ngưỡng (p6 S8).
     *
     * <p>Cả hai điều kiện đều cần. Chỉ "kẹp ở đầu mút" thì bắt được hầu hết ca, nhưng một mẫu tình
     * cờ vừa nằm ngoài đầu mút vừa sát một mức sẽ bị gắn cờ oan; chỉ "ΔE00 lớn" thì lại báo ngoài
     * dải cho mọi màu nằm giữa hai mức (ΔE00 lớn là bình thường ở giữa dải).
     */
    private static boolean outOfRangeCheck(Lab sample, List<PhChartPoint> points,
                                           List<Lab> references, DeltaE2000Params params) {
        int nearest = 0;
        double nearestDistance = Double.MAX_VALUE;
        for (int i = 0; i < references.size(); i++) {
            double distance = DeltaE2000.deltaE(sample, references.get(i), params);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = i;
            }
        }
        boolean clampedAtEnd = nearest == 0
                ? DeltaE2000.deltaE(sample, references.get(1), params) > nearestDistance
                : nearest == references.size() - 1
                        ? DeltaE2000.deltaE(sample, references.get(nearest - 1), params) > nearestDistance
                        : false;
        double tolerance = Math.max(OUT_OF_RANGE_TOLERANCE, points.get(nearest).toleranceDeltaE());
        return clampedAtEnd && nearestDistance > tolerance;
    }

    /**
     * Phần trăm khớp.
     *
     * <p>Cố tình <b>không</b> đưa về gần 100%: con số trung thực phải phản ánh ΔE00 thật. Trong design
     * có "Khớp 98%" — giá trị đó là ảo, và p6 §6.13 yêu cầu thay bằng {@code matchPercent} thật
     * (thường 85–95). Hàm này cho 100 khi ΔE00 = 0 và 0 khi ΔE00 ≥ {@link #MATCH_SCALE_DE}.
     */
    public static double matchPercent(double deltaE00) {
        double ratio = 1.0 - deltaE00 / MATCH_SCALE_DE;
        return Math.round(Math.max(0.0, Math.min(1.0, ratio)) * 100.0);
    }

    /** Làm tròn 1 chữ số thập phân — mọi pH hiển thị và lưu đều ở dạng này. */
    public static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Chênh lệch pH giữa khớp tương đối và khớp bảng DB — vượt ngưỡng thì gắn {@code CARD_CHART_MISMATCH}. */
    public static boolean mismatches(double relativePh, double databasePh) {
        return Math.abs(relativePh - databasePh) > CARD_CHART_MISMATCH_PH;
    }

    /** Mức gần nhất, dùng khi cần hiển thị tên màu đi kèm kết quả. */
    public static Optional<PhChartPoint> nearestPoint(Lab sample, PhChart chart) {
        return chart.points().stream()
                .min((x, y) -> Double.compare(
                        DeltaE2000.deltaE(sample, x.lab(), chart.deltaEParams()),
                        DeltaE2000.deltaE(sample, y.lab(), chart.deltaEParams())));
    }
}
