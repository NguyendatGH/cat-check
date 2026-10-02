package com.catcheck.cat.domain;

/**
 * Sáu dấu hiệu lâm sàng chủ nuôi tự khai (p4 §4.4.4, p6 §6.9.6).
 *
 * <p>Đây là <b>tập hằng số</b>, không phải danh mục cấu hình: đổi danh sách này phải kèm migration,
 * nên p4 chọn {@code VARCHAR(32)[]} + ràng buộc tập con {@code <@} thay vì bảng tra cứu.</p>
 *
 * <p>Khả năng "kêu đau" không suy ra được từ ảnh cát và không suy ra được từ màu, nên nó nằm ở đây
 * là câu hỏi hỏi trực tiếp chủ nuôi. Tập này KHÔNG dùng để chẩn đoán, không tính điểm nguy cơ và
 * không hiện tên bệnh (quyết định #6, #8).</p>
 */
public enum ClinicalSign {

    /** Rặn nhiều nhưng ra rất ít. */
    STRAINING,

    /** Vào khay nhiều lần mà không ra nước tiểu. */
    NO_URINE,

    /** Kêu đau. */
    CRYING,

    /** Thấy màu đỏ hoặc hồng. */
    BLOOD_VISIBLE,

    /** Bỏ ăn, nằm li bì. */
    LETHARGY_ANOREXIA,

    /** Liếm bất thường. */
    EXCESSIVE_LICKING
}
