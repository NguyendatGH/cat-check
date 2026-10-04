package com.catcheck.notification.domain;

/**
 * Tuỳ chọn kênh/tần suất — bảng {@code user_notification_preference} (p4 F4).
 *
 * <p><b>Không phải consent.</b> Tuỳ chọn mang tính pháp lý nằm ở {@code consent_record};
 * p12 §12.9.2 cấm trộn hai thứ. Vì đây không phải căn cứ pháp lý nên các cờ ở đây ĐƯỢC phép
 * mặc định bật (khác với consent — tuyệt đối không được tick sẵn).</p>
 *
 * <p>Module {@code identity} sở hữu luồng GHI (p8 B11). notification chỉ ĐỌC.</p>
 */
public record UserNotificationPreference(
        AttentionAlertChannel attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        QuietHours quietHours) {

    /** Nguyên văn cột DEFAULT của V13 — dùng khi dòng chưa tồn tại (p4 F4 ghi chú 1-1 bắt buộc). */
    public static UserNotificationPreference defaults() {
        return new UserNotificationPreference(
                AttentionAlertChannel.PUSH_AND_INAPP, true, true, false, false, QuietHours.defaults());
    }

    /** Công tắc tương ứng {@link PreferenceGate} có đang bật không. */
    public boolean allows(PreferenceGate gate) {
        return switch (gate) {
            case NONE -> true;
            case CREDIT_ALERTS -> creditAlertsEnabled;
            case REPORT_READY -> reportReadyEnabled;
            case IMAGE_RETENTION_WARNING -> imageRetentionWarningEnabled;
            case NORMAL_RESULT -> normalResultEnabled;
            // Không tắt được thông báo, chỉ hạ kênh — xem allowsPush().
            case ATTENTION_CHANNEL -> true;
        };
    }

    /** Riêng cảnh báo "cần chú ý": {@code INAPP_ONLY} nghĩa là hạ kênh, không phải tắt. */
    public boolean allowsPushFor(PreferenceGate gate) {
        if (gate == PreferenceGate.ATTENTION_CHANNEL) {
            return attentionAlertChannel == AttentionAlertChannel.PUSH_AND_INAPP;
        }
        return allows(gate);
    }
}
