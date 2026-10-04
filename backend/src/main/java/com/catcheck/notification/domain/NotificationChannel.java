package com.catcheck.notification.domain;

/**
 * Kênh gửi — {@code notification.channel} (p4 F2, enum p4 §4.4.7).
 *
 * <p>Enum trên dây = enum trong DB, giống hệt từng ký tự (04-index quy tắc 5).</p>
 */
public enum NotificationChannel {
    /** Web push qua FCM. */
    PUSH,
    /** Email transactional qua {@code email_outbox}. */
    EMAIL,
    /**
     * Hộp thư trong app — <b>kênh nền, không tắt được</b> (p12 §12.1). Mọi thông báo
     * không-marketing luôn có một dòng {@code IN_APP}, bất kể push/email có bật hay không.
     */
    IN_APP
}
