package com.catcheck.content.domain;

/**
 * Loại nội dung (p4 H1). {@code TIP} là mẹo ngắn, {@code ARTICLE} là bài có heading và BẮT BUỘC có
 * {@code body_md} (ràng buộc {@code ck_care_tip_article_body}), {@code FAQ} là câu hỏi thường gặp.
 *
 * <p>Lưu dạng {@code VARCHAR + CHECK} (p4 §4.1.3): Hibernate ghi bằng {@code STRING}, không dùng
 * {@code @Enumerated(ORDINAL)} — đổi thứ tự hằng số sau này sẽ hỏng dữ liệu đã lưu.</p>
 */
public enum CareTipKind {

    TIP,
    ARTICLE,
    FAQ
}
