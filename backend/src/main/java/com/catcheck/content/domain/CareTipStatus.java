package com.catcheck.content.domain;

/**
 * Trạng thái vòng đời bài viết (p4 H1, p4 §4.4.8). Chuyển trạng thái đều ghi audit_log (CONTENT.*) theo p14 §14.3.5.
 *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không dùng
 * {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum CareTipStatus {

    DRAFT,
    IN_REVIEW,
    PUBLISHED,
    ARCHIVED
}
