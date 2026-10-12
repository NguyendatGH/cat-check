package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.NotificationPreferences;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;

public record NotificationPreferencesResponse(
        String attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        boolean quietHoursEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursStart,
        @JsonFormat(pattern = "HH:mm") LocalTime quietHoursEnd) {

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
