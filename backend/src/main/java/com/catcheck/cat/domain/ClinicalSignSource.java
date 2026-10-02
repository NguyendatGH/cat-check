package com.catcheck.cat.domain;

/**
 * Nơi phát sinh khai báo dấu hiệu lâm sàng (p4 §4.4.4). *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum ClinicalSignSource {

    RESULT_SCREEN,
    SURVEY,
    MANUAL
}
