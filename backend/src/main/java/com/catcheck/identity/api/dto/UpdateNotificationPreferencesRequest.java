package com.catcheck.identity.api.dto;

import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AttentionAlertChannel;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.shared.error.BusinessRuleException;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

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
