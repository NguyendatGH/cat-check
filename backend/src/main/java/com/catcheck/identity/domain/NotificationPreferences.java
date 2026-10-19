package com.catcheck.identity.domain;

import java.time.LocalTime;

public record NotificationPreferences(
        AttentionAlertChannel attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {

    public static NotificationPreferences defaults() {
        return new NotificationPreferences(
                AttentionAlertChannel.PUSH_AND_INAPP,
                true,
                true,
                false,
                false,
                true,
                LocalTime.of(22, 0),
                LocalTime.of(7, 0));
    }
}
