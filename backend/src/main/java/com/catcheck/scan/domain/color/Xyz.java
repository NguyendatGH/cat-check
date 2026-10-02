package com.catcheck.scan.domain.color;

/**
 * Một điểm XYZ trắc quan, chuẩn hoá theo <b>D65/2°</b>.
 *
 * <p>Kiểu này là mắt xích trung gian giữa linear RGB và CIELAB. Giữ nó thành kiểu riêng (thay vì
 * {@code double[3]}) để không thể vô tình hoán đổi thứ tự kênh — lỗi dễ mắc nhất khi chuyển về
 * sau, và cũng để hằng số whitepoint chỉ xuất hiện ở đúng một chỗ.
 *
 * <h2>Vì sao whitepoint lấy từ tổng hàng của ma trận chứ không dùng số của CIE</h2>
 * <p>Có hai hệ số whitepoint D65 đang lưu hành: {@code (0.95047, 1, 1.08883)} do CIE công bố, và
 * giá trị suy ra từ chính ma trận sRGB→XYZ là {@code (0.9504559, 1, 1.0890578)}. Chúng lệch nhau
 * ~1.4e-5 ở thành phần X và ~2.3e-4 ở thành phần Z. Nếu lấy số của CIE rồi dùng ma trận chuẩn,
 * ảnh trắng tuyệt đối
 * {@code #FFFFFF} sẽ ra {@code a* ≈ −0.0025, b* ≈ +0.0039} thay vì đúng 0 — tức là ảnh trắng tuyệt
 * đối bị coi là hơi xanh/anh. Sai số 0.003 ΔE00 là vô hại, nhưng nó phá vỡ điều kiện khởi đầu của
 * mọi phép hiệu chuẩn ánh sáng trắng (white balance) và làm test "trắng phải trung tính" không bao
 * giờ đóng. Ở đây chọn giá trị <b>tự nhất quán</b> với ma trận: {@link #D65_WHITE_POINT}.
 *
 * @param x thành phần X
 * @param y thành phần Y (luminance tương đối)
 * @param z thành phần Z
 */
public record Xyz(double x, double y, double z) {

    /**
     * D65/2° — <b>tổng hàng</b> của ma trận IEC 61966-2-1, tức ánh sáng trắng mà chính định nghĩa
     * sRGB dùng. Xem giải thích ở Javadoc lớp.
     */
    public static final Xyz D65_WHITE_POINT =
            new Xyz(0.4123907992659595 + 0.35758433938387796 + 0.1804807884018343,
                    0.21263900587151036 + 0.7151686787677559 + 0.07219231536073371,
                    0.019330818715591851 + 0.11919477979462599 + 0.9505321522496606);

    /** Số do CIE công bố — chỉ để đối chiếu tài liệu, <b>không</b> dùng để tính toán. */
    public static final Xyz D65_CIE_PUBLISHED = new Xyz(0.95047, 1.00000, 1.08883);

    /** Ánh sáng trung tính tương đương D65 — cũng là {@code (Xn, Yn, Zn)}. */
    public static final Xyz D65 = D65_WHITE_POINT;

    public Xyz {
        if (Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z)) {
            throw new IllegalArgumentException("Gia tri XYZ khong hop le (NaN)");
        }
    }

    public static Xyz ofArray(double[] xyz) {
        if (xyz == null || xyz.length < 3) {
            throw new IllegalArgumentException("Can mang XYZ du 3 phan tu [X,Y,Z]");
        }
        return new Xyz(xyz[0], xyz[1], xyz[2]);
    }

    public double component(int index) {
        return switch (index) {
            case 0 -> x;
            case 1 -> y;
            case 2 -> z;
            default -> throw new IllegalArgumentException("Chi so kenh XYZ phai la 0, 1 hoac 2");
        };
    }

    public double[] toArray() {
        return new double[] {x, y, z};
    }

    @Override
    public String toString() {
        return "Xyz[" + x + ", " + y + ", " + z + "]";
    }
}
