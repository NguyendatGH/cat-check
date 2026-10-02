package com.catcheck.identity.api.dto;

import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AttentionAlertChannel;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.shared.error.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

/**
 * B12 — body của {@code PUT /account/notification-preferences} (p8 §8.5).
 *
 * <p>{@code PUT} = thay TOÀN BỘ biểu diễn (p8 §8.1.11), nên cả tám field đều bắt buộc.</p>
 *
 * <p>Kiểu bọc ({@code Boolean}, không phải {@code boolean}) để phân biệt "client gửi
 * {@code false}" với "client không gửi field" — {@code boolean} nguyên thuỷ sẽ âm thầm thành
 * {@code false} và một field bị quên trở thành một lần tắt thông báo ngoài ý muốn.</p>
 */
public record UpdateNotificationPreferencesRequest(
        String attentionAlertChannel,
        Boolean creditAlertsEnabled,
        Boolean reportReadyEnabled,
        Boolean imageRetentionWarningEnabled,
        Boolean normalResultEnabled,
        Boolean quietHoursEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursStart,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursEnd) {

    public static NotificationPreferences toPreferences(UpdateNotificationPreferencesRequest r) {
        return new NotificationPreferences(
                parseChannel(r.attentionAlertChannel()),
                required(r.creditAlertsEnabled(), "creditAlertsEnabled"),
                required(r.reportReadyEnabled(), "reportReadyEnabled"),
                required(r.imageRetentionWarningEnabled(), "imageRetentionWarningEnabled"),
                required(r.normalResultEnabled(), "normalResultEnabled"),
                required(r.quietHoursEnabled(), "quietHoursEnabled"),
                requiredTime(r.quietHoursStart(), "quietHoursStart"),
                requiredTime(r.quietHoursEnd(), "quietHoursEnd"));
    }

    /**
     * Giá trị lạ — kể cả một giá trị "OFF" mà client tự nghĩ ra — trả 400 chứ không âm thầm
     * rơi về mặc định: p4 F4 cố ý không có đường tắt hẳn cảnh báo cần chú ý (quyết định #10).
     */
    private static AttentionAlertChannel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BusinessRuleException(IdentityErrorCode.VALIDATION_FAILED, "attentionAlertChannel");
        }
        try {
            return AttentionAlertChannel.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(IdentityErrorCode.VALIDATION_FAILED, "attentionAlertChannel");
        }
    }

    private static boolean required(Boolean value, String field) {
        if (value == null) {
            throw new BusinessRuleException(IdentityErrorCode.VALIDATION_FAILED, field);
        }
        return value;
    }

    private static LocalTime requiredTime(LocalTime value, String field) {
        if (value == null) {
            throw new BusinessRuleException(IdentityErrorCode.VALIDATION_FAILED, field);
        }
        return value;
    }
}
