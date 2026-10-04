package com.catcheck.notification.domain;

/** {@code email_outbox.status} và {@code notification_outbox.status} (p4 F5). */
public enum OutboxStatus {
    /** Chờ worker lấy việc (`next_attempt_at <= now`). */
    PENDING,
    /** Đã đẩy đi thành công. */
    SENT,
    /** Hết {@code maxAttempts} — dead-letter logic, admin gửi lại thủ công (p12 §12.8.1). */
    FAILED
}
