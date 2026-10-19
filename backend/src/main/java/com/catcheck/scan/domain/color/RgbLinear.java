package com.catcheck.scan.domain.color;

public record RgbLinear(double r, double g, double b) {

    public static final RgbLinear BLACK = new RgbLinear(0.0, 0.0, 0.0);

    public static final RgbLinear WHITE = new RgbLinear(1.0, 1.0, 1.0);

    public RgbLinear {
        if (Double.isNaN(r) || Double.isNaN(g) || Double.isNaN(b)) {
            throw new IllegalArgumentException("Gia tri RGB tuyien tinh khong hop le (NaN)");
        }
    }

    public RgbLinear times(double gainR, double gainG, double gainB) {
        return new RgbLinear(r * gainR, g * gainG, b * gainB);
    }

    public RgbLinear times(double factor) {
        return new RgbLinear(r * factor, g * factor, b * factor);
    }

    public double component(int index) {
        return switch (index) {
            case 0 -> r;
            case 1 -> g;
            case 2 -> b;
            default -> throw new IllegalArgumentException("Chi so kenh RGB phai la 0, 1 hoac 2");
        };
    }

    public double mean() {
        return (r + g + b) / 3.0;
    }

    public double saturation() {
        double max = Math.max(r, Math.max(g, b));
        double min = Math.min(r, Math.min(g, b));
        return max <= 0.0 ? 0.0 : (max - min) / max;
    }

    public double[] toArray() {
        return new double[] {r, g, b};
    }

    public static RgbLinear ofArray(double[] rgb) {
        if (rgb == null || rgb.length < 3) {
            throw new IllegalArgumentException("Can mang RGB du 3 phan tu [r,g,b]");
        }
        return new RgbLinear(rgb[0], rgb[1], rgb[2]);
    }

    public static RgbLinear ofSrgb8(int r8, int g8, int b8) {
        return ColorSpace.srgb8ToLinear(r8, g8, b8);
    }

    @Override
    public String toString() {
        return "RgbLinear[" + r + ", " + g + ", " + b + "]";
    }
}
