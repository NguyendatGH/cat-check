package com.catcheck.notification.domain;

/** {@code notification.status} (p4 F2 + §4.4.7). */
public enum NotificationStatus {
    /** Chờ gửi (push/email đã có dòng outbox tương ứng). */
    QUEUED,
    /** Đã gửi. Dòng {@code IN_APP} được đánh {@code SENT} ngay khi ghi — bản ghi chính là nơi nhận. */
    SENT,
    /** Hết số lần thử, vào dead-letter (p12 §12.8.1). */
    FAILED,
    /**
     * <b>Quyết định không gửi</b> vì user tắt loại thông báo này hoặc chưa cấp consent — vẫn ghi
     * dòng để sau này giải thích được "vì sao tôi không nhận được" (p4 F2 ghi chú nghiệp vụ).
     */
    SUPPRESSED
}
