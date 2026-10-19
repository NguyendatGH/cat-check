package com.catcheck.scan.domain.color;

public final class WhiteBalanceSolver {

    public static final double MAX_GAIN = 3.0;

    public static final double MIN_GAIN = 1.0 / MAX_GAIN;

    private WhiteBalanceSolver() {
        throw new AssertionError("WhiteBalanceSolver la lop tien ich, khong instantiate");
    }

    public record Gains(double r, double g, double b) {

        public RgbLinear apply(RgbLinear rgb) {
            return rgb.times(r, g, b);
        }

        public double[] toArray() {
            return new double[] {r, g, b};
        }
    }

    public static Gains fromNeutralPoint(RgbLinear neutralPoint) {
        double scale = neutralPoint.mean();
        if (scale <= 1e-6) {
            throw new IllegalArgumentException("Diem tham chieu qua toi, khong the can bang trang");
        }
        return new Gains(
                clampGain(scale / neutralPoint.r()),
                clampGain(scale / neutralPoint.g()),
                clampGain(scale / neutralPoint.b()));
    }

    public static Gains fallback(java.util.List<RgbLinear> backgroundPixels) {
        if (backgroundPixels == null || backgroundPixels.isEmpty()) {
            throw new IllegalArgumentException("Khong co pixel nen de uoc luong can bang trang");
        }
        RgbLinear grayWorld = grayWorld(backgroundPixels);
        RgbLinear whitePatch = whitePatch(backgroundPixels);
        RgbLinear blended = new RgbLinear(
                0.5 * (grayWorld.r() + whitePatch.r()),
                0.5 * (grayWorld.g() + whitePatch.g()),
                0.5 * (grayWorld.b() + whitePatch.b()));
        return fromNeutralPoint(blended);
    }

    public static Gains fallbackToReference(java.util.List<RgbLinear> backgroundPixels,
                                            RgbLinear referencePoint) {
        if (backgroundPixels == null || backgroundPixels.isEmpty()) {
            throw new IllegalArgumentException("Khong co pixel nen de uoc luong can bang trang");
        }
        RgbLinear grayWorld = grayWorld(backgroundPixels);
        RgbLinear whitePatch = whitePatch(backgroundPixels);
        RgbLinear measured = new RgbLinear(
                0.5 * (grayWorld.r() + whitePatch.r()),
                0.5 * (grayWorld.g() + whitePatch.g()),
                0.5 * (grayWorld.b() + whitePatch.b()));
        return fromReferencePoints(measured, referencePoint);
    }

    static Gains fromReferencePoints(RgbLinear measured, RgbLinear reference) {
        double measuredMean = measured.mean();
        double referenceMean = reference.mean();
        if (measuredMean <= 1e-6 || referenceMean <= 1e-6
                || measured.r() <= 1e-6 || measured.g() <= 1e-6 || measured.b() <= 1e-6
                || reference.r() <= 1e-6 || reference.g() <= 1e-6 || reference.b() <= 1e-6) {
            throw new IllegalArgumentException("Diem nen do duoc/tham chieu qua toi de can bang mau");
        }
        return new Gains(
                clampGain((reference.r() / referenceMean) / (measured.r() / measuredMean)),
                clampGain((reference.g() / referenceMean) / (measured.g() / measuredMean)),
                clampGain((reference.b() / referenceMean) / (measured.b() / measuredMean)));
    }

    public static RgbLinear grayWorld(java.util.List<RgbLinear> pixels) {
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        for (RgbLinear pixel : pixels) {
            sr += pixel.r();
            sg += pixel.g();
            sb += pixel.b();
        }
        int n = pixels.size();
        return new RgbLinear(sr / n, sg / n, sb / n);
    }

    public static RgbLinear whitePatch(java.util.List<RgbLinear> pixels) {
        java.util.List<RgbLinear> sorted = new java.util.ArrayList<>(pixels);
        sorted.sort(java.util.Comparator.comparingDouble(RgbLinear::mean).reversed());
        int take = Math.max(1, (int) Math.floor(sorted.size() * 0.05));
        double sr = 0.0;
        double sg = 0.0;
        double sb = 0.0;
        for (int i = 0; i < take; i++) {
            sr += sorted.get(i).r();
            sg += sorted.get(i).g();
            sb += sorted.get(i).b();
        }
        return new RgbLinear(sr / take, sg / take, sb / take);
    }

    private static double clampGain(double gain) {
        if (Double.isNaN(gain) || Double.isInfinite(gain)) {
            return 1.0;
        }
        return Math.max(MIN_GAIN, Math.min(MAX_GAIN, gain));
    }
}
