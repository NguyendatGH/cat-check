package com.catcheck.scan.domain.color;

/**
 * Chuyển đổi màu <b>thuần Java</b>: sRGB ↔ linear RGB ↔ XYZ ↔ CIELAB, cố định
 * <b>D65 / observer 2°</b> (p6 §6.5 S5).
 *
 * <h2>⛔ Cấm tuyệt đối: {@code cv::cvtColor(BGR2Lab)} trên ảnh 8-bit</h2>
 * <p><b>Không được</b> dùng {@code cv::cvtColor(..., COLOR_BGR2Lab, CV_8U)} ở bất kỳ đâu trong
 * pipeline. Lý do (nguyên văn từ p6 §6.5 S5 và research-color-pipeline mục S5): biến thể 8-bit
 * của OpenCV đưa {@code L*} vào dải {@code [0, 255]} và dịch {@code a*, b*} vào {@code [0, 255]}
 * với offset 128. Hai hệ quả:
 * <ol>
 *   <li>Mất độ chính xác: {@code a*, b*} đo được bị lệch tới hàng chục đơn vị ΔE khi truyền qua
 *       đường 8-bit, trong khi ΔE00 phân biệt được mức chỉ cách nhau ~3.</li>
 *   <li>Sai quy ước: code đọc kết quả như Lab chuẩn sẽ hiểu {@code L* = 128} là giữa sáng thay
 *       vì {@code L* = 50}. Loại lỗi này không bao giờ biểu hiện thành crash — chỉ thành kết quả
 *       sai, đây là loại bug tệ nhất trong một sản phẩm đo đạc.</li>
 * </ol>
 * <p>Vì vậy toàn bộ phép chuyển đổi nằm ở đây, trên {@code double}, và OpenCV chỉ loại "ra số
 * nguyên 8-bit từ ảnh" (p6 §6.5.5 — danh sách hàm CV được phép dùng không hề có {@code cvtColor}).
 */
public final class ColorSpace {

    /** Ma trận chuẩn IEC 61966-2-1: linear sRGB → XYZ, whitepoint D65, dòng theo X, Y, Z. */
    private static final double[] LINEAR_SRGB_TO_XYZ = {
            0.4123907992659595, 0.35758433938387796, 0.1804807884018343,
            0.21263900587151036, 0.7151686787677559, 0.07219231536073371,
            0.019330818715591851, 0.11919477979462599, 0.9505321522496606
    };

    /** Nghịch đảo của {@link #LINEAR_SRGB_TO_XYZ}: XYZ → linear sRGB. */
    private static final double[] XYZ_TO_LINEAR_SRGB = {
            3.2409699419045213, -1.5373831775700935, -0.4986107602930033,
            -0.9692436362808798, 1.8759675015077206, 0.04155505740717561,
            0.05563007969699361, -0.20397695888897657, 1.0569715142428786
    };

    /**
     * Hằng số hàm phi tuyến CIELAB. {@code (6/29)^3 = 0.008856…} là điểm gãy; {@code 3·(6/29)^2} và
     * {@code 4/29} là hệ số của nhánh tuyến tính, bảo đảm liên tục C1 tại điểm gãy.
     */
    private static final double DELTA = 6.0 / 29.0;
    private static final double DELTA_CUBED = DELTA * DELTA * DELTA;
    private static final double LINEAR_SLOPE = 1.0 / (3.0 * DELTA * DELTA);
    private static final double LINEAR_INTERCEPT = 4.0 / 29.0;

    /** Ngưỡng dưới đó coi là gần {@code 0} tuyệt đối khi khử EOTF (tránh phân số với số 0). */
    private static final double MIN_LINEAR = 1e-12;

    private ColorSpace() {
        throw new AssertionError("ColorSpace la lop tien ich, khong instantiate");
    }

    // ---------------------------------------------------------------- sRGB EOTF

    /**
     * sRGB 8-bit (0–255) → linear trong {@code [0, 1]}. Đây là <b>inverse sRGB EOTF</b> —
     * bước bắt buộc đầu tiên của luồng "admin nhập hex" (p6 §6.6.3) và của mọi pixel ảnh.
     */
    public static double srgb8ToLinear(int channel8) {
        return srgbToLinear(clamp01(channel8 / 255.0));
    }

    /** sRGB đã gamma-encode trong {@code [0, 1]} → linear. */
    public static double srgbToLinear(double encoded) {
        double c = clamp01(encoded);
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    /** linear → sRGB đã gamma-encode (nghịch đảo của {@link #srgbToLinear}). */
    public static double linearToSrgb(double linear) {
        double c = linear <= MIN_LINEAR ? 0.0 : linear;
        if (c > 1.0) {
            // Giá trị vượt 1 chỉ xuất hiện khi khuếch đại để hiển thị; nén lại thay vì ném lỗi,
            // để lời gọi "vẽ swatch" không bao giờ làm hỏng cả pipeline.
            c = 1.0;
        }
        return c <= 0.0031308 ? 12.92 * c : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    /** linear → sRGB 8-bit, có kẹp về {@code [0, 255]}. */
    public static int linearToSrgb8(double linear) {
        return (int) Math.round(clamp01(linearToSrgb(linear)) * 255.0);
    }

    /** {@code #RRGGBB} (không phân biệt hoa thường) → linear RGB. Ném lỗi nếu sai định dạng. */
    public static RgbLinear hexToLinearRgb(String hex) {
        Srgb8 rgb = hexToRgb8(hex);
        return RgbLinear.ofSrgb8(rgb.r(), rgb.g(), rgb.b());
    }

    /** {@code #RRGGBB} → sRGB 8-bit. Chấp nhận {@code #} tuỳ chọn; ném lỗi nếu sai định dạng. */
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

    /** RGB 8-bit đã gamma-encode → linear. */
    public static RgbLinear srgb8ToLinear(int r8, int g8, int b8) {
        return new RgbLinear(srgb8ToLinear(r8), srgb8ToLinear(g8), srgb8ToLinear(b8));
    }

    // ---------------------------------------------------------------- XYZ ↔ Lab

    /** linear sRGB → XYZ(D65). */
    public static Xyz linearRgbToXyz(RgbLinear rgb) {
        double x = LINEAR_SRGB_TO_XYZ[0] * rgb.r() + LINEAR_SRGB_TO_XYZ[1] * rgb.g() + LINEAR_SRGB_TO_XYZ[2] * rgb.b();
        double y = LINEAR_SRGB_TO_XYZ[3] * rgb.r() + LINEAR_SRGB_TO_XYZ[4] * rgb.g() + LINEAR_SRGB_TO_XYZ[5] * rgb.b();
        double z = LINEAR_SRGB_TO_XYZ[6] * rgb.r() + LINEAR_SRGB_TO_XYZ[7] * rgb.g() + LINEAR_SRGB_TO_XYZ[8] * rgb.b();
        return new Xyz(x, y, z);
    }

    /** XYZ(D65) → linear sRGB (có thể âm khi ngoài gamut — <b>không</b> kẹp ở đây). */
    public static RgbLinear xyzToLinearRgb(Xyz xyz) {
        double r = XYZ_TO_LINEAR_SRGB[0] * xyz.x() + XYZ_TO_LINEAR_SRGB[1] * xyz.y() + XYZ_TO_LINEAR_SRGB[2] * xyz.z();
        double g = XYZ_TO_LINEAR_SRGB[3] * xyz.x() + XYZ_TO_LINEAR_SRGB[4] * xyz.y() + XYZ_TO_LINEAR_SRGB[5] * xyz.z();
        double b = XYZ_TO_LINEAR_SRGB[6] * xyz.x() + XYZ_TO_LINEAR_SRGB[7] * xyz.y() + XYZ_TO_LINEAR_SRGB[8] * xyz.z();
        return new RgbLinear(r, g, b);
    }

    /** XYZ(D65) → CIELAB(D65/2°). */
    public static Lab xyzToLab(Xyz xyz) {
        double fx = labF(xyz.x() / Xyz.D65_WHITE_POINT.x());
        double fy = labF(xyz.y() / Xyz.D65_WHITE_POINT.y());
        double fz = labF(xyz.z() / Xyz.D65_WHITE_POINT.z());
        return new Lab(116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz));
    }

    /** CIELAB(D65/2°) → XYZ(D65). */
    public static Xyz labToXyz(Lab lab) {
        double fy = (lab.l() + 16.0) / 116.0;
        double fx = fy + lab.a() / 500.0;
        double fz = fy - lab.b() / 200.0;
        double x = labInverseF(fx) * Xyz.D65_WHITE_POINT.x();
        double y = labInverseF(fy) * Xyz.D65_WHITE_POINT.y();
        double z = labInverseF(fz) * Xyz.D65_WHITE_POINT.z();
        return new Xyz(x, y, z);
    }

    // ---------------------------------------------------------------- tiện ích tổng hợp

    /** linear sRGB → CIELAB(D65/2°) — điểm vào chính của pipeline (S5, sau CCM). */
    public static Lab linearRgbToLab(RgbLinear rgb) {
        return xyzToLab(linearRgbToXyz(rgb));
    }

    /** CIELAB(D65/2°) → linear sRGB (có thể ngoài gamut). */
    public static RgbLinear labToLinearRgb(Lab lab) {
        return xyzToLinearRgb(labToXyz(lab));
    }

    /** sRGB 8-bit → linear (luồng admin nhập hex, p6 §6.6.3). */
    public static Lab srgb8ToLab(int r8, int g8, int b8) {
        return linearRgbToLab(srgb8ToLinear(r8, g8, b8));
    }

    /** {@code #RRGGBB} → CIELAB(D65/2°). Chính là bước mà seed placeholder phải đi qua. */
    public static Lab hexToLab(String hex) {
        Srgb8 rgb = hexToRgb8(hex);
        return srgb8ToLab(rgb.r(), rgb.g(), rgb.b());
    }

    /** CIELAB(D65/2°) → {@code #RRGGBB} đã kẹp trong gamut. Chỉ dùng để vẽ swatch, không dùng để đo. */
    public static String labToHex(Lab lab) {
        RgbLinear linear = labToLinearRgb(lab);
        return String.format("#%02X%02X%02X",
                linearToSrgb8(linear.r()), linearToSrgb8(linear.g()), linearToSrgb8(linear.b()));
    }

    /**
     * Ma trận chuyển đổi dùng cho ảnh trong bản ghi {@code scan_analysis.ccm} — trả về mảng 9 phần
     * tử hàng-chính để serialize thẳng vào {@code jsonb} (p6 §6.5.3, cột {@code ccm.M}).
     */
    public static double[] linearSrgbToXyzMatrix() {
        return LINEAR_SRGB_TO_XYZ.clone();
    }

    // ---------------------------------------------------------------- nội bộ

    /** Hàm phi tuyến tiền {@code f(t)} của CIELAB, đã chuẩn hoá theo whitepoint. */
    private static double labF(double t) {
        return t > DELTA_CUBED ? Math.cbrt(t) : LINEAR_SLOPE * t + LINEAR_INTERCEPT;
    }

    /** Nghịch đảo {@link #labF}. */
    private static double labInverseF(double t) {
        return t > DELTA ? t * t * t : 3.0 * DELTA * DELTA * (t - LINEAR_INTERCEPT);
    }

    private static double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        return value > 1.0 ? 1.0 : value;
    }

    /**
     * RGB 8-bit đã gamma-encode. Tách thành kiểu riêng để {@link #hexToLinearRgb} không phải
     * parse cùng một chuỗi ba lần.
     */
    public record Srgb8(int r, int g, int b) {
        public Srgb8 {
            if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
                throw new IllegalArgumentException("Kenh sRGB 8-bit phai trong [0,255]");
            }
        }
    }
}
