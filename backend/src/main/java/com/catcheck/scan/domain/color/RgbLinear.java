package com.catcheck.scan.domain.color;

/**
 * Một điểm màu RGB <b>tuyến tính</b> sRGB — cùng miền giá trị với XYZ (D65), nên {@code 1.0} ứng với
 * trắng tuyệt đối.
 *
 * <p>Cố ý tách khỏi "sRGB 8-bit" (0–255, đã gamma-encode) vì toàn bộ phép hiệu chuẩn màu (white
 * balance von Kries, CCM 3×3, khử tone curve) <b>chỉ đúng trong miền tuyến tính</b> (p6 S4).
 * Làm phép nhân trên dữ liệu đã gamma-encode là lỗi kinh điển làm CCM hội tụ về ma trận đơn vị.
 *
 * @param r thành phần đỏ tuyến tính
 * @param g thành phần xanh lá tuyến tính
 * @param b thành phần xanh dương tuyến tính
 */
public record RgbLinear(double r, double g, double b) {

    /** Đen tuyệt đối. */
    public static final RgbLinear BLACK = new RgbLinear(0.0, 0.0, 0.0);

    /** Trắng tuyệt đối — cũng là giá trị mà white balance hướng về (von Kries, p6 S4 bước 2). */
    public static final RgbLinear WHITE = new RgbLinear(1.0, 1.0, 1.0);

    public RgbLinear {
        if (Double.isNaN(r) || Double.isNaN(g) || Double.isNaN(b)) {
            throw new IllegalArgumentException("Gia tri RGB tuyien tinh khong hop le (NaN)");
        }
    }

    /** Nhân từng thành phần với một bộ gain (white balance von Kries là phép nhân này). */
    public RgbLinear times(double gainR, double gainG, double gainB) {
        return new RgbLinear(r * gainR, g * gainG, b * gainB);
    }

    /** Nhân thành phần thứ nhất với một hệ số đơn — tiện cho phép scale toàn cục. */
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

    /** Trung bình ba kênh — cơ sở của phép cân bằng trắng kiểu gray-world (p6 S4b). */
    public double mean() {
        return (r + g + b) / 3.0;
    }

    /** Sắc độ tương đối (giá trị 1.0 nghĩa là ba kênh bằng nhau — màu trung tính về hue). */
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

    /**
     * Dựng từ ba byte sRGB <b>đã gamma-encode</b>, kèm khử sRGB EOTF.
     *
     * <p>Cần khử gamma ở đây chứ không chỉ chia 255: kiểu này cam kết là <em>tuyến tính</em>, mà CCM
     * và white balance chỉ đúng ở miền tuyến tính. Một hàm tên "ofSrgb8" mà giữ nguyên gamma sẽ
     * khiến cả những chỗ gọi trông vô hại (đọc màu tham chiếu của card, ô màu seed trong DB) âm thầm
     * làm sai phép ước lượng.
     */
    public static RgbLinear ofSrgb8(int r8, int g8, int b8) {
        return ColorSpace.srgb8ToLinear(r8, g8, b8);
    }

    @Override
    public String toString() {
        return "RgbLinear[" + r + ", " + g + ", " + b + "]";
    }
}
