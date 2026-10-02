package com.catcheck.content.domain;

/**
 * Chủ đề, dùng cho bộ lọc {@code ?category=} của F7. Cột rỗng nghĩa là chưa phân loại — giá trị
 * rỗng hợp lệ, KHÔNG phải enum bắt buộc, nên DB chỉ ràng buộc khi khác NULL.
 *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}.</p>
 */
public enum CareTipCategory {

    HYDRATION,
    LITTER,
    SCAN_HOWTO,
    DIET,
    GENERAL
}
