package com.catcheck.notification.domain;

import java.util.Optional;

public enum NotificationTemplate {


    AUTH_OTP_REGISTER(false, false, true, false, true, false,
            PreferenceGate.NONE, null, "email.auth.otp_register"),
    AUTH_OTP_RESET_PASSWORD(false, false, true, false, true, false,
            PreferenceGate.NONE, null, "email.auth.otp_reset"),


    WELCOME_ONBOARDING(true, false, true, true, false, false,
            PreferenceGate.NONE, null, "email.onboarding.welcome"),
    REMINDER_SCAN_DUE(true, true, true, true, false, false,
            PreferenceGate.NONE, ConsentPurposes.HEALTH_REMINDER_EMAIL, "notif.reminder.scan_due"),
    REMINDER_OVERDUE(true, true, false, true, false, false,
            PreferenceGate.NONE, null, "notif.reminder.overdue"),
    SCAN_RESULT_ATTENTION(true, true, false, false, true, false,
            PreferenceGate.ATTENTION_CHANNEL, null, "notif.scan.attention"),
    SCAN_RESULT_NORMAL(true, false, false, true, false, false,
            PreferenceGate.NORMAL_RESULT, null, "notif.scan.normal"),
    CREDIT_EXPIRING_T48H(true, true, false, true, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expiring_48h"),
    CREDIT_EXPIRING_T6H(true, true, false, false, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expiring_6h"),
    CREDIT_EXPIRED(true, false, true, true, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expired"),
    ACTIVATION_SUCCESS(true, false, true, false, true, false,
            PreferenceGate.NONE, null, "notif.credit.activation_success"),
    REPORT_PDF_READY(true, true, true, true, false, false,
            PreferenceGate.REPORT_READY, null, "notif.report.pdf_ready"),
    PRIVACY_EXPORT_READY(false, false, false, false, true, false,
            PreferenceGate.NONE, null, "email.privacy.export_ready"),
    IMAGE_RETENTION_WARNING(true, false, false, false, false, false,
            PreferenceGate.IMAGE_RETENTION_WARNING, null, "notif.scan.image_expiring"),
    SYSTEM_MAINTENANCE(true, true, true, false, true, false,
            PreferenceGate.NONE, null, "notif.system.maintenance"),
    PRIVACY_INCIDENT_NOTICE(true, false, true, false, true, false,
            PreferenceGate.NONE, null, "email.privacy.incident_notice"),
    MARKETING_PROMOTION(false, true, true, true, false, true,
            PreferenceGate.NONE, ConsentPurposes.MARKETING_EMAIL, "notif.marketing.promotion");

    private final boolean inAppRecord;
    private final boolean pushChannel;
    private final boolean emailChannel;
    private final boolean quietHoursApply;
    private final boolean mandatory;
    private final boolean marketing;
    private final PreferenceGate preferenceGate;
    private final String emailConsentPurpose;
    private final String i18nKey;

    NotificationTemplate(boolean inAppRecord, boolean pushChannel, boolean emailChannel,
                         boolean quietHoursApply, boolean mandatory, boolean marketing,
                         PreferenceGate preferenceGate, String emailConsentPurpose, String i18nKey) {
        this.inAppRecord = inAppRecord;
        this.pushChannel = pushChannel;
        this.emailChannel = emailChannel;
        this.quietHoursApply = quietHoursApply;
        this.mandatory = mandatory;
        this.marketing = marketing;
        this.preferenceGate = preferenceGate;
        this.emailConsentPurpose = emailConsentPurpose;
        this.i18nKey = i18nKey;
    }

    public String code() {
        return name();
    }

    public boolean writesInAppRecord() {
        return inAppRecord;
    }

    public boolean supportsPush() {
        return pushChannel;
    }

    public boolean supportsEmail() {
        return emailChannel;
    }

    public boolean quietHoursApply() {
        return quietHoursApply;
    }

    public boolean mandatory() {
        return mandatory;
    }

    public boolean marketing() {
        return marketing;
    }

    public PreferenceGate preferenceGate() {
        return preferenceGate;
    }

    public String i18nKey() {
        return i18nKey;
    }

    public Optional<String> pushConsentPurpose() {
        if (!pushChannel) {
            return Optional.empty();
        }
        return Optional.of(marketing ? ConsentPurposes.MARKETING_PUSH : ConsentPurposes.HEALTH_REMINDER_PUSH);
    }

    public Optional<String> emailConsentPurpose() {
        return Optional.ofNullable(emailConsentPurpose);
    }

    public static Optional<NotificationTemplate> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        for (NotificationTemplate template : values()) {
            if (template.name().equals(code)) {
                return Optional.of(template);
            }
        }
        return Optional.empty();
    }

    public static final class ConsentPurposes {

        public static final String HEALTH_REMINDER_PUSH = "HEALTH_REMINDER_PUSH";
        public static final String HEALTH_REMINDER_EMAIL = "HEALTH_REMINDER_EMAIL";
        public static final String MARKETING_PUSH = "MARKETING_PUSH";
        public static final String MARKETING_EMAIL = "MARKETING_EMAIL";

        private ConsentPurposes() {
        }
    }
}
