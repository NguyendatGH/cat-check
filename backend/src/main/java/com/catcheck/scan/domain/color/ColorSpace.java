package com.catcheck.scan.domain.color;

public final class ColorSpace {

    private static final double[] LINEAR_SRGB_TO_XYZ = {
            0.4123907992659595, 0.35758433938387796, 0.1804807884018343,
            0.21263900587151036, 0.7151686787677559, 0.07219231536073371,
            0.019330818715591851, 0.11919477979462599, 0.9505321522496606
    };

    private static final double[] XYZ_TO_LINEAR_SRGB = {
            3.2409699419045213, -1.5373831775700935, -0.4986107602930033,
            -0.9692436362808798, 1.8759675015077206, 0.04155505740717561,
            0.05563007969699361, -0.20397695888897657, 1.0569715142428786
    };

    private static final double DELTA = 6.0 / 29.0;
    private static final double DELTA_CUBED = DELTA * DELTA * DELTA;
    private static final double LINEAR_SLOPE = 1.0 / (3.0 * DELTA * DELTA);
    private static final double LINEAR_INTERCEPT = 4.0 / 29.0;

    private static final double MIN_LINEAR = 1e-12;

    private ColorSpace() {
        throw new AssertionError("ColorSpace la lop tien ich, khong instantiate");
    }


    public static double srgb8ToLinear(int channel8) {
        return srgbToLinear(clamp01(channel8 / 255.0));
    }

    public static double srgbToLinear(double encoded) {
        double c = clamp01(encoded);
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double linearToSrgb(double linear) {
        double c = linear <= MIN_LINEAR ? 0.0 : linear;
        if (c > 1.0) {
            c = 1.0;
        }
        return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    public static int linearToSrgb8(double linear) {
        return (int) Math.round(clamp01(linearToSrgb(linear)) * 255.0);
    }

    public static RgbLinear hexToLinearRgb(String hex) {
        Srgb8 rgb = hexToRgb8(hex);
        return RgbLinear.ofSrgb8(rgb.r(), rgb.g(), rgb.b());
    }

    public static Srgb8 hexToRgb8(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("Chuoi hex khong duoc null");
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6 || !s.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Hex sRGB khong hop le (can #RRGGBB): " + hex);
        }
        return new Srgb8(Integer.parseInt(s.substring(0, 2), 16),
                Integer.parseInt(s.substring(2, 4), 16),
                Integer.parseInt(s.substring(4, 6), 16));
    }

    public static RgbLinear srgb8ToLinear(int r8, int g8, int b8) {
        return new RgbLinear(srgb8ToLinear(r8), srgb8ToLinear(g8), srgb8ToLinear(b8));
    }


    public static Xyz linearRgbToXyz(RgbLinear rgb) {
        double x = LINEAR_SRGB_TO_XYZ[0] * rgb.r() + LINEAR_SRGB_TO_XYZ[1] * rgb.g() + LINEAR_SRGB_TO_XYZ[2] * rgb.b();
        double y = LINEAR_SRGB_TO_XYZ[3] * rgb.r() + LINEAR_SRGB_TO_XYZ[4] * rgb.g() + LINEAR_SRGB_TO_XYZ[5] * rgb.b();
        double z = LINEAR_SRGB_TO_XYZ[6] * rgb.r() + LINEAR_SRGB_TO_XYZ[7] * rgb.g() + LINEAR_SRGB_TO_XYZ[8] * rgb.b();
        return new Xyz(x, y, z);
    }

    public static RgbLinear xyzToLinearRgb(Xyz xyz) {
        double r = XYZ_TO_LINEAR_SRGB[0] * xyz.x() + XYZ_TO_LINEAR_SRGB[1] * xyz.y() + XYZ_TO_LINEAR_SRGB[2] * xyz.z();
        double g = XYZ_TO_LINEAR_SRGB[3] * xyz.x() + XYZ_TO_LINEAR_SRGB[4] * xyz.y() + XYZ_TO_LINEAR_SRGB[5] * xyz.z();
        double b = XYZ_TO_LINEAR_SRGB[6] * xyz.x() + XYZ_TO_LINEAR_SRGB[7] * xyz.y() + XYZ_TO_LINEAR_SRGB[8] * xyz.z();
        return new RgbLinear(r, g, b);
    }

    public static Lab xyzToLab(Xyz xyz) {
        double fx = labF(xyz.x() / Xyz.D65_WHITE_POINT.x());
        double fy = labF(xyz.y() / Xyz.D65_WHITE_POINT.y());
        double fz = labF(xyz.z() / Xyz.D65_WHITE_POINT.z());
        return new Lab(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
    }

    public static Xyz labToXyz(Lab lab) {
        double fy = (lab.l() + 16.0) / 116.0;
        double fx = fy + lab.a() / 500.0;
        double fz = fy - lab.b() / 200.0;
        double x = labInverseF(fx) * Xyz.D65_WHITE_POINT.x();
        double y = labInverseF(fy) * Xyz.D65_WHITE_POINT.y();
        double z = labInverseF(fz) * Xyz.D65_WHITE_POINT.z();
        return new Xyz(x, y, z);
    }


    public static Lab linearRgbToLab(RgbLinear rgb) {
        return xyzToLab(linearRgbToXyz(rgb));
    }

    public static RgbLinear labToLinearRgb(Lab lab) {
        return xyzToLinearRgb(labToXyz(lab));
    }

    public static Lab srgb8ToLab(int r8, int g8, int b8) {
        return linearRgbToLab(srgb8ToLinear(r8, g8, b8));
    }

    public static Lab hexToLab(String hex) {
        Srgb8 rgb = hexToRgb8(hex);
        return srgb8ToLab(rgb.r(), rgb.g(), rgb.b());
    }

    public static String labToHex(Lab lab) {
        RgbLinear linear = labToLinearRgb(lab);
        return String.format("#%02X%02X%02X",
                linearToSrgb8(linear.r()), linearToSrgb8(linear.g()), linearToSrgb8(linear.b()));
    }

    public static double[] linearSrgbToXyzMatrix() {
        return LINEAR_SRGB_TO_XYZ.clone();
    }


    private static double labF(double t) {
        return t > DELTA_CUBED ? Math.cbrt(t) : LINEAR_SLOPE * t + LINEAR_INTERCEPT;
    }

    private static double labInverseF(double t) {
        return t > DELTA ? t * t * t : 3.0 * DELTA * DELTA * (t - LINEAR_INTERCEPT);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return value > 1.0 ? 1.0 : value;
    }

    public record Srgb8(int r, int g, int b) {
        public Srgb8 {
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                throw new IllegalArgumentException("Kenh sRGB 8-bit phai trong [0,255]");
            }
        }
    }
}
