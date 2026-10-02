package com.catcheck.cat.domain;

/**
 * Câu 2 của bộ khảo sát 5 câu (C29, p4 §4.4.4). *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không
 * dùng {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum UrinaryHistory {

    NONE,
    PAST,
    CURRENT
}
