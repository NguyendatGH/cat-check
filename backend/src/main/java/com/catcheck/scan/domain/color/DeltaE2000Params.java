package com.catcheck.scan.domain.color;

/**
 * Bộ tham số trọng số của CIEDE2000.
 *
 * <p>CatCheck <b>cố ý</b> dùng {@code kL = 2, kC = 1, kH = 1} thay vì {@code (1, 1, 1)} mặc định
 * (p6 §6.5.1 (a) và research-color-pipeline S8). Lý do: {@code L*} là kênh bị nhiễu nặng nhất bởi
 * những thứ <em>không liên quan tới pH</em> — độ ướt làm sẫm, bóng đổ, góc chiếu, độ sâu hạt trong
 * cát. Thông tin pH nằm ở chroma và hue. Giảm trọng số {@code L} (giống CIE94 graphic-arts) làm
 * phép khớp ổn định hơn rõ rệt.
 *
 * <p>Quan trọng: <b>giá trị {@code kL = 2} KHÔNG được dùng khi đối chiếu với bộ 34 cặp chuẩn
 * Sharma et al.</b> — bộ đó được tính với {@code (1, 1, 1)}. Xem {@link DeltaE2000Test}.
 *
 * @param kL hệ số trọng số độ sáng
 * @param kC hệ số trọng số chroma
 * @param kH hệ số trọng số hue
 */
public record DeltaE2000Params(double kL, double kC, double kH) {

    /** Bộ chuẩn của CIE — giá trị mà cả 34 cặp Sharma et al. dùng để sinh bảng tham chiếu. */
    public static final DeltaE2000Params STANDARD = new DeltaE2000Params(1.0, 1.0, 1.0);

    /** Bộ dùng cho mọi phép so khớp bảng màu của CatCheck (p6 §6.5.1 S8). */
    public static final DeltaE2000Params CAT_CHECK = new DeltaE2000Params(2.0, 1.0, 1.0);

    public DeltaE2000Params {
        if (!(kL > 0.0) || !(kC > 0.0) || !(kH > 0.0)) {
            throw new IllegalArgumentException("kL, kC, kH phai > 0 nhung gia tri: " + kL + ", " + kC + ", " + kH);
        }
    }
}
