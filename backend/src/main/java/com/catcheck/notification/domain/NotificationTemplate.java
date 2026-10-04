package com.catcheck.notification.domain;

import java.util.Optional;

/**
 * Registry 15 template của p12 §12.2.2 + §12.2.6.
 *
 * <p><b>Vì sao là enum trong code chứ không phải bảng:</b> p4 §4.4.7 chốt
 * {@code notification.template_code} là {@code VARCHAR(48)} <b>không CHECK</b> và ghi rõ
 * "validate ở service theo registry template". Đây chính là registry đó. Giá trị
 * {@code name()} = giá trị ghi xuống cột, không được đổi.</p>
 *
 * <p>Ba cột quyết định hành vi, lấy nguyên từ bảng p12 §12.2.6:</p>
 * <ul>
 *   <li>{@code quietHoursApply} — có hoãn push/email qua giờ im lặng không.</li>
 *   <li>{@code mandatory} — "bắt buộc gửi kể cả khi user tắt thông báo": bỏ qua
 *       {@link PreferenceGate}, KHÔNG bỏ qua consent (consent là căn cứ pháp lý, không phải
 *       tuỳ chọn trải nghiệm — p12 §12.9.2).</li>
 *   <li>{@code marketing} — marketing là NGOẠI LỆ DUY NHẤT của quy tắc "luôn ghi in-app"
 *       (p12 §12.1).</li>
 * </ul>
 */
public enum NotificationTemplate {

    /* --- Giao dịch/bảo mật: gửi trước khi user tồn tại nên KHÔNG có dòng in-app --- */

    AUTH_OTP_REGISTER(false, false, true, false, true, false,
            PreferenceGate.NONE, null, "email.auth.otp_register"),
    AUTH_OTP_RESET_PASSWORD(false, false, true, false, true, false,
            PreferenceGate.NONE, null, "email.auth.otp_reset"),

    /* --- Sản phẩm/dịch vụ (p12 §12.2.2) --- */

    WELCOME_ONBOARDING(true, false, true, true, false, false,
            PreferenceGate.NONE, null, "email.onboarding.welcome"),
    REMINDER_SCAN_DUE(true, true, true, true, false, false,
            PreferenceGate.NONE, ConsentPurposes.HEALTH_REMINDER_EMAIL, "notif.reminder.scan_due"),
    REMINDER_OVERDUE(true, true, false, true, false, false,
            PreferenceGate.NONE, null, "notif.reminder.overdue"),
    /** Quiet hours KHÔNG áp dụng — "an toàn ưu tiên hơn giấc ngủ" (p12 §12.2.6). */
    SCAN_RESULT_ATTENTION(true, true, false, false, true, false,
            PreferenceGate.ATTENTION_CHANNEL, null, "notif.scan.attention"),
    SCAN_RESULT_NORMAL(true, false, false, true, false, false,
            PreferenceGate.NORMAL_RESULT, null, "notif.scan.normal"),
    CREDIT_EXPIRING_T48H(true, true, false, true, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expiring_48h"),
    /** Quiet hours KHÔNG áp dụng — còn 6 giờ, dời sang 07:00 là mất credit (p12 §12.2.6). */
    CREDIT_EXPIRING_T6H(true, true, false, false, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expiring_6h"),
    CREDIT_EXPIRED(true, false, true, true, false, false,
            PreferenceGate.CREDIT_ALERTS, null, "notif.credit.expired"),
    /** Giao dịch: in-app bắt buộc, email tắt được (p12 §12.2.6). */
    ACTIVATION_SUCCESS(true, false, true, false, true, false,
            PreferenceGate.NONE, null, "notif.credit.activation_success"),
    REPORT_PDF_READY(true, true, true, true, false, false,
            PreferenceGate.REPORT_READY, null, "notif.report.pdf_ready"),
    /** DSAR export is a legal notice: bypass preference/consent and quiet hours; address is pinned to dsar_request. */
    PRIVACY_EXPORT_READY(false, false, false, false, true, false,
            PreferenceGate.NONE, null, "email.privacy.export_ready"),
    /** Chỉ in-app, không đẩy ⇒ quiet hours không phát sinh (p12 §12.2.3). */
    IMAGE_RETENTION_WARNING(true, false, false, false, false, false,
            PreferenceGate.IMAGE_RETENTION_WARNING, null, "notif.scan.image_expiring"),
    SYSTEM_MAINTENANCE(true, true, true, false, true, false,
            PreferenceGate.NONE, null, "notif.system.maintenance"),
    /** Phase sau. Marketing KHÔNG ghi in-app và dùng cặp consent riêng (p12 §12.9.2). */
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

    /** Mã template như ghi xuống {@code notification.template_code} / {@code email_outbox.template_code}. */
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

    /**
     * Purpose cần có để được đẩy push. p12 §12.1: {@code HEALTH_REMINDER_PUSH} cho nhắc theo
     * dõi / credit / kết quả scan, {@code MARKETING_PUSH} cho khuyến mãi — hai purpose TÁCH
     * RIÊNG, không gộp một checkbox (p15 §15.3.2).
     */
    public Optional<String> pushConsentPurpose() {
        if (!pushChannel) {
            return Optional.empty();
        }
        return Optional.of(marketing ? ConsentPurposes.MARKETING_PUSH : ConsentPurposes.HEALTH_REMINDER_PUSH);
    }

    /**
     * Purpose cần có để được gửi email. Rỗng = không cần consent riêng (căn cứ {@code TT} —
     * thực hiện thoả thuận, p12 §12.4.1).
     */
    public Optional<String> emailConsentPurpose() {
        return Optional.ofNullable(emailConsentPurpose);
    }

    /** Tra theo giá trị cột; rỗng nếu mã không có trong registry (p4 §4.4.7: validate ở service). */
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

    /** Mã purpose trong {@code consent_purpose} (p15 §15.3.2) — hằng để không gõ sai chuỗi. */
    public static final class ConsentPurposes {

        public static final String HEALTH_REMINDER_PUSH = "HEALTH_REMINDER_PUSH";
        public static final String HEALTH_REMINDER_EMAIL = "HEALTH_REMINDER_EMAIL";
        public static final String MARKETING_PUSH = "MARKETING_PUSH";
        public static final String MARKETING_EMAIL = "MARKETING_EMAIL";

        private ConsentPurposes() {
        }
    }
}
