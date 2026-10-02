package com.catcheck.scan.domain.color;

/**
 * Bước <b>S9 — confidence &amp; khoảng tin cậy</b> (p6 §6.5.1 S9, nghiên cứu color-pipeline S9).
 *
 * <h2>Vì sao trung bình nhân</h2>
 * <p>Confidence là <b>trung bình nhân có trọng số</b> của tám thành phần chất lượng, không phải trung
 * bình cộng. Ảnh mờ thì <em>không thể</em> được "bù" bằng việc thấy thẻ rõ — đó là lý do vật lý, và
 * cũng là lý do phải dùng trung bình nhân. Với trung bình cộng, một ảnh rất mờ cộng một thẻ rất đẹp
 * vẫn ra confidence trung bình, tức hệ thống khẳng định điều mà nó không biết.
 *
 * <h2>Vì sao luôn có trần theo tier</h2>
 * <p>Ngay cả khi mọi thành phần đều tốt, nhánh không có thẻ (S4b) hoặc không hiệu chuẩn được chỉ
 * bằng chính bề mặt cát vẫn bị trần: A 0.95 / B 0.60 / C 0.35. Đây là ranh giới trung thực về
 * <em>nguồn</em> bằng chứng, không phải về chất lượng ảnh.
 */
public final class ConfidenceCalculator {

    /** Trần confidence theo phương pháp hiệu chuẩn (nghiên cứu S4b). */
    public enum CalibrationTier {
        /** Có thẻ + CCM + linearization: trần 0.95. */
        A(0.95),
        /** Chỉ white balance từ bề mặt cát: trần 0.60. */
        B(0.60),
        /** Không hiệu chuẩn được: trần 0.35 — gần như luôn {@code INCONCLUSIVE}. */
        C(0.35);

        private final double cap;

        CalibrationTier(double cap) {
            this.cap = cap;
        }

        public double cap() {
            return cap;
        }
    }

    /** Ngưỡng dưới mà kết quả là {@code INCONCLUSIVE} (p6 S9, mặc định 0.40). */
    public static final double MIN_RESULT_CONFIDENCE = 0.40;

    /** Bán rộng CI tối thiểu/tối đa (p6 S9). */
    public static final double CI_MIN = 0.1;
    public static final double CI_MAX = 0.8;

    /** Hệ số 1.96 của khoảng tin cậy 95%. */
    public static final double Z_95 = 1.96;

    private ConfidenceCalculator() {
        throw new AssertionError("ConfidenceCalculator la lop tien ich, khong instantiate");
    }

    /**
     * Mọi thành phần đầu vào của S9, đã chuẩn hoá về {@code [0,1]}.
     *
     * @param calibrationResidualDeltaE ΔE00 dư của phép hiệu chuẩn (S4)
     * @param deltaEMin                 ΔE00 khớp nhỏ nhất (S8)
     * @param spreadDeltaE              độ đồng màu của các hạt (S7)
     * @param indicatorPixelRatio       tỉ lệ pixel hạt chỉ thị (S6)
     * @param blurVariance              biến thiên nét (S2)
     * @param blurMin                   ngưỡng nét tối thiểu
     * @param clipHigh                  tỉ lệ pixel cháy
     * @param clipLow                   tỉ lệ pixel tối hẳn
     * @param lumaGradient              độ không đều của ánh sáng
     * @param blobCount                 số hạt dùng được
     */
    public record Inputs(
            double calibrationResidualDeltaE,
            double deltaEMin,
            double spreadDeltaE,
            double indicatorPixelRatio,
            double blurVariance,
            double blurMin,
            double clipHigh,
            double clipLow,
            double lumaGradient,
            int blobCount) {

        public Inputs {
            if (blurMin <= 0.0) {
                throw new IllegalArgumentException("blurMin phai > 0, nhan duoc " + blurMin);
            }
        }
    }

    /**
     * Kết quả S9.
     *
     * @param confidence    điểm [0,1] đã áp trần theo tier
     * @param phLow         cận dưới khoảng tin cậy
     * @param phHigh        cận trên
     * @param band          mức hiển thị của confidence
     */
    public record Result(double confidence, double phLow, double phHigh, Band band) {

        public boolean isInconclusive() {
            return confidence < MIN_RESULT_CONFIDENCE;
        }
    }

    /** Mức hiển thị confidence. */
    public enum Band {
        HIGH, MEDIUM, LOW
    }

    /**
     * Tính confidence và khoảng tin cậy pH.
     *
     * @param inputs      thành phần chất lượng
     * @param phEstimate  pH ước lượng từ S8
     * @param chart       bảng màu dùng để lấy độ dốc cục bộ (pH trên ΔE00)
     * @param deltaEMin   ΔE00 khớp đã dùng ở S8
     * @param spreadDeltaE độ đồng màu đã dùng ở S7
     * @param blobCount   số hạt đã dùng ở S7
     * @param perpResidual ΔE00 của phần dư theo phương vuông góc đường cong tại điểm khớp
     * @param tier        tier hiệu chuẩn để áp trần
     * @param config      tham số cấu hình (ngưỡng mục tiêu)
     */
    public static Result calculate(Inputs inputs,
                                   double phEstimate,
                                   PhChart chart,
                                   double deltaEMin,
                                   double spreadDeltaE,
                                   int blobCount,
                                   double perpResidual,
                                   CalibrationTier tier,
                                   Thresholds config) {

        double cCal = clamp(1.0 - (inputs.calibrationResidualDeltaE() - 1.0) / 5.0);
        double cFit = clamp(1.0 - (deltaEMin - 2.0) / 8.0);
        double cSpread = clamp(1.0 - (spreadDeltaE - 2.0) / 6.0);
        double cCover = clamp(inputs.indicatorPixelRatio() / config.targetCoverage());
        double cFocus = clamp((inputs.blurVariance() - inputs.blurMin()) / (2 * inputs.blurMin()));
        double cExpo = clamp(1.0 - (inputs.clipHigh() / 0.02 + inputs.clipLow() / 0.05) / 2);
        double cLight = clamp(1.0 - inputs.lumaGradient() / config.lumaGradientMax());
        double cN = Math.max(0.4, Math.min(1.0, inputs.blobCount() / (double) config.targetBlobs()));

        double confidence = weightedGeometricMean(
                new double[] {cCal, cFit, cSpread, cCover, cFocus, cExpo, cLight, cN},
                new double[] {3, 3, 2, 2, 1, 1, 1, 1});
        confidence = Math.min(confidence, tier.cap());

        // --- CI: lan truyền sai số ΔE00 qua độ dốc cục bộ của bảng màu (pH trên ΔE00).
        // residualΔE là residual hiệu chuẩn S4 (cùng số với c_cal); perpResidual chỉ dùng ở
        // lệch phạt. Trộn hai đại lượng này sẽ cho CI sai theo cả hai chiều.
        double gradient = localGradient(chart, phEstimate);
        double residual = inputs.calibrationResidualDeltaE();
        double sigmaDeltaE = Math.sqrt(
                spreadDeltaE * spreadDeltaE / Math.max(1, blobCount) + residual * residual);
        double sigmaPh = gradient * (sigmaDeltaE + perpResidual * config.perpPenalty());
        double halfWidth = Math.max(CI_MIN, Math.min(CI_MAX, Z_95 * sigmaPh));

        double phLow = ChartMatcher.round1(Math.max(0.0, phEstimate - halfWidth));
        double phHigh = ChartMatcher.round1(phEstimate + halfWidth);

        return new Result(round2(confidence), phLow, phHigh, bandOf(confidence));
    }

    /**
     * Trung bình nhân có trọng số.
     *
     * <p>Tính bằng {@code log} để tránh dưới luồng khi có nhiều thành phần nhỏ: tích trực tiếp của
     * các lũy thừa có thể tràn số 0 với double.
     */
    static double weightedGeometricMean(double[] values, double[] weights) {
        double logSum = 0.0;
        double weightSum = 0.0;
        for (int i = 0; i < values.length; i++) {
            double v = Math.max(values[i], 1e-9);
            logSum += weights[i] * Math.log(v);
            weightSum += weights[i];
        }
        return Math.exp(logSum / weightSum);
    }

    /**
     * Độ dốc cục bộ của bảng màu: <b>bao nhiêu pH trên mỗi đơn vị ΔE00</b> tại ngay phH ước lượng.
     *
     * <p>Ý nghĩa vật lý: vùng bảng màu "dốc" (một khoảng pH nhỏ mà màu đổi nhiều) cho CI hẹp; vùng
     * "phẳng" (hai mức pH gần màu) cho CI rộng. Nhờ vậy CI tự nói lên đúng giới hạn vật lý của chỉ thị
     * thay vì dùng một hằng số bịa ra.
     */
    static double localGradient(PhChart chart, double ph) {
        if (chart == null || chart.points().size() < 2) {
            return 1.0;
        }
        var points = chart.points();
        DeltaE2000Params params = chart.deltaEParams();

        PhChartPoint nearest = null;
        double nearestGap = Double.MAX_VALUE;
        for (PhChartPoint point : points) {
            double gap = Math.abs(point.phValue() - ph);
            if (gap < nearestGap) {
                nearestGap = gap;
                nearest = point;
            }
        }
        int index = points.indexOf(nearest);
        int lowerIndex = Math.max(0, Math.min(index, points.size() - 2));
        PhChartPoint lower = points.get(lowerIndex);
        PhChartPoint upper = points.get(lowerIndex + 1);

        double deltaE = lower.deltaETo(upper, params);
        if (deltaE < 1e-6) {
            return 1.0;
        }
        return Math.abs(upper.phValue() - lower.phValue()) / deltaE;
    }

    private static Band bandOf(double confidence) {
        if (confidence >= 0.75) {
            return Band.HIGH;
        }
        return confidence >= MIN_RESULT_CONFIDENCE ? Band.MEDIUM : Band.LOW;
    }

    private static double clamp(double value) {
        if (Double.isNaN(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Ngưỡng cấu hình — tập trung tham số để không rò rỉ magic number vào logic.
     *
     * @param targetCoverage   tỉ lệ pixel hạt chỉ thị mục tiêu (STANDARD 0.015)
     * @param targetBlobs      số hạt mục tiêu (20)
     * @param lumaGradientMax  ngưỡng không đều ánh sáng
     * @param perpPenalty      hệ số phạt phần dư vuông góc (0.5)
     */
    public record Thresholds(double targetCoverage, int targetBlobs, double lumaGradientMax,
                             double perpPenalty) {

        /** Bộ ngưỡng mặc định của dòng STANDARD (p6 S6/S9). */
        public static Thresholds standard() {
            return new Thresholds(0.015, 20, 40.0, 0.5);
        }
    }
}
