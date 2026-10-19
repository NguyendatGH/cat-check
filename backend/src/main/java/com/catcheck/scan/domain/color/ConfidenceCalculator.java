package com.catcheck.scan.domain.color;

public final class ConfidenceCalculator {

    public enum CalibrationTier {
        A(0.95),
        B(0.60),
        C(0.35);

        private final double cap;

        CalibrationTier(double cap) {
            this.cap = cap;
        }

        public double cap() {
            return cap;
        }
    }

    public static final double MIN_RESULT_CONFIDENCE = 0.40;

    public static final double CI_MIN = 0.1;
    public static final double CI_MAX = 0.8;

    public static final double Z_95 = 1.96;

    private ConfidenceCalculator() {
        throw new AssertionError("ConfidenceCalculator la lop tien ich, khong instantiate");
    }

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

    public record Result(double confidence, double phLow, double phHigh, Band band) {

        public boolean isInconclusive() {
            return confidence < MIN_RESULT_CONFIDENCE;
        }
    }

    public enum Band {
        HIGH, MEDIUM, LOW
    }

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

    public record Thresholds(double targetCoverage, int targetBlobs, double lumaGradientMax,
                             double perpPenalty) {

        public static Thresholds standard() {
            return new Thresholds(0.015, 20, 40.0, 0.5);
        }
    }
}
