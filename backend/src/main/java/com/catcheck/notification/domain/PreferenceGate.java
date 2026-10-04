package com.catcheck.notification.domain;

/**
 * Công tắc trong {@code user_notification_preference} quyết định một template có được gửi hay
 * không (p12 §12.9.1). KHÔNG phải consent — consent nằm ở {@code consent_record} và p12 §12.9.2
 * cấm trộn hai thứ.
 */
public enum PreferenceGate {
    /** Không có công tắc nào — template luôn được phép (vẫn còn consent gate cho push/email). */
    NONE,
    /** {@code credit_alerts_enabled}. */
    CREDIT_ALERTS,
    /** {@code report_ready_enabled}. */
    REPORT_READY,
    /** {@code image_retention_warning_enabled} — mặc định TẮT (p12 §12.2.3). */
    IMAGE_RETENTION_WARNING,
    /** {@code normal_result_enabled} — mặc định TẮT. */
    NORMAL_RESULT,
    /**
     * {@code attention_alert_channel}: không tắt được thông báo, chỉ hạ kênh push → in-app
     * (p12 §12.9.1). Vì vậy gate này KHÔNG bao giờ chặn dòng in-app.
     */
    ATTENTION_CHANNEL
}
