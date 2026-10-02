package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.NotificationPreferences;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * B11/B12 — tuỳ chọn thông báo (body trả về của cả GET lẫn PUT).
 *
 * <p>Tám field phẳng theo ĐÚNG p8 §8.5 (bảng body của
 * {@code PUT /account/notification-preferences}), nguồn dữ liệu là bảng
 * {@code user_notification_preference} mà p8 B11 chỉ đích danh.</p>
 */
public record NotificationPreferencesResponse(
        String attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        boolean quietHoursEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursStart,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursEnd) {

    /**
     * Map từ record miền. Đặt ở đây chứ không phải helper private trong
     * {@code AccountController}: ArchUnit R4 quét <b>mọi</b> method của {@code @RestController},
     * kể cả private, nên helper nhận/trả kiểu {@code ..domain..} sẽ làm fail build.
     */
    public static NotificationPreferencesResponse of(NotificationPreferences p) {
        return new NotificationPreferencesResponse(
                p.attentionAlertChannel().name(),
                p.creditAlertsEnabled(),
                p.reportReadyEnabled(),
                p.imageRetentionWarningEnabled(),
                p.normalResultEnabled(),
                p.quietHoursEnabled(),
                p.quietHoursStart(),
                p.quietHoursEnd());
    }
}
