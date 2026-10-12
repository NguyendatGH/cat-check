package com.catcheck.notification.domain;

public record UserNotificationPreference(
        AttentionAlertChannel attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        QuietHours quietHours) {

    public static UserNotificationPreference defaults() {
        return new UserNotificationPreference(
                AttentionAlertChannel.PUSH_AND_INAPP, true, true, false, false, QuietHours.defaults());
    }

    public boolean allows(PreferenceGate gate) {
        return switch (gate) {
            case NONE -> true;
            case CREDIT_ALERTS -> creditAlertsEnabled;
            case REPORT_READY -> reportReadyEnabled;
            case IMAGE_RETENTION_WARNING -> imageRetentionWarningEnabled;
            case NORMAL_RESULT -> normalResultEnabled;
            case ATTENTION_CHANNEL -> true;
        };
    }

    public boolean allowsPushFor(PreferenceGate gate) {
        if (gate == PreferenceGate.ATTENTION_CHANNEL) {
            return attentionAlertChannel == AttentionAlertChannel.PUSH_AND_INAPP;
        }
        return allows(gate);
    }
}
