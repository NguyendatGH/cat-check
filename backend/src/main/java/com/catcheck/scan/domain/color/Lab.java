package com.catcheck.scan.domain.color;

/**
 * Một điểm màu CIELAB (D65, observer 2°) — đơn vị dữ liệu duy nhất mà toàn bộ pipeline dùng để
 * so khớp bảng màu và tính ΔE00.
 *
 * <p>Bất biến theo p6 §6.5 S5: {@code l ∈ [0, 100]}, {@code a, b} không chặn trên/dưới theo lý
 * thuyết nhưng thực tế nằm trong {@code [-128, 127]}. Không ép range trong constructor vì
 * backfill S8–S10 phải chấp nhận {@code lab_l/a/b} đọc thẳng từ DB (có thể đã bị lệch do bản
 * cũ), và việc clamp âm thầm sẽ che lỗi dữ liệu thay vì báo.
 *
 * @param l {@code L* ∈ [0, 100]}
 * @param a {@code a*}
 * @param b {@code b*}
 */
public record Lab(double l, double a, double b) {

    public static final double MIN_L = 0.0;
    public static final double MAX_L = 100.0;

    public Lab {
        if (Double.isNaN(l) || Double.isNaN(a) || Double.isNaN(b)) {
            throw new IllegalArgumentException("Gia tri Lab khong hop le (NaN): " + l + ", " + a + ", " + b);
        }
    }

    /**
     * Tạo từ mảng 3 phần tử theo thứ tự {@code [L, a, b]} — dùng khi đọc từ
     * {@code scan_analysis.lab_l/lab_a/lab_b} để backfill (p6 §6.5.4).
     */
    public static Lab of(double[] lab) {
        if (lab == null || lab.length < 3) {
            throw new IllegalArgumentException("Can mang Lab du 3 phan tu [L,a,b]");
        }
        return new Lab(lab[0], lab[1], lab[2]);
    }

    /** {@code C*ab = sqrt(a² + b²)} — độ rực, dùng cho mọi ngưỡng chroma (S4b, S6). */
    public double chroma() {
        return Math.hypot(a, b);
    }

    /**
     * Góc hue {@code [0, 360)} theo quy ước CIELAB (0° = đỏ dương, 90° = vàng, 180° = xanh lá,
     * 270° = xanh lam). Trả {@code 0} khi {@code C*ab == 0} (màu trung tính — hue không xác định).
     */
    public double hueDegrees() {
        double h = Math.toDegrees(Math.atan2(b, a));
        return h < 0.0 ? h + 360.0 : h;
    }

    /** Khoảng cách Euclid trong không gian Lab — chỉ dùng để hình học (chiếu vuông góc, phân cụm). */
    public double distanceTo(Lab other) {
        double dl = l - other.l;
        double da = a - other.a;
        double db = b - other.b;
        return Math.sqrt(dl * dl + da * da + db * db);
    }

    /** Trung tâm của hai điểm theo tọa độ — dùng cho phép nội suy trên polyline bảng màu. */
    public static Lab midpoint(Lab p, Lab q, double t) {
        return new Lab(
                p.l + t * (q.l - p.l),
                p.a + t * (q.a - p.a),
                p.b + t * (q.b - p.b));
    }

    /** Cộng vector (tọa độ Lab cộng trực tiếp) — dùng cho trung bình. */
    public Lab plus(double dl, double da, double db) {
        return new Lab(l + dl, a + da, b + db);
    }

    /** Nhân với hệ số (dùng cho trung bình có trọng số và trung bình nhân trọng số). */
    public Lab times(double factor) {
        return new Lab(l * factor, a * factor, b * factor);
    }

    public double[] toArray() {
        return new double[] {l, a, b};
    }

    @Override
    public String toString() {
        return "Lab[L=" + l + ", a=" + a + ", b=" + b + "]";
    }
}
