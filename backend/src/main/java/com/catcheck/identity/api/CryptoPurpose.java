package com.catcheck.identity.api;

/**
 * Muc dich cua khoa con trong HKDF-SHA256 (p11 §11.10.2).
 *
 * <p>Moi muc dich tach san HKDF, nen khoa PII cua {@code phone} khong bao gio bi dung
 * de giai ma {@code secret_enc} cua TOTP va nguoc lai.</p>
 */
public enum CryptoPurpose {

    /** {@code app_user.phone} — van hoa o M1, cac muc dich khac ghi ro {@code sinceVersion}. */
    PII_PHONE("pii.phone"),

    /** Token push FCM — module notification. */
    PII_PUSH_TOKEN("pii.push_token"),

    /** Secret TOTP — {@code user_mfa_totp.secret_enc}. */
    MFA_TOTP("mfa.totp"),

    /** Toa do cua nguoi dung — module location. */
    PII_LOCATION("pii.location");

    private final String label;

    CryptoPurpose(String label) {
        this.label = label;
    }

    /** Nhan dung voi {@code crypto_canary.purpose}. */
    public String label() {
        return label;
    }

    public static CryptoPurpose fromLabel(String value) {
        for (CryptoPurpose candidate : values()) {
            if (candidate.label.equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Muc dich ma hoa khong ho tro: " + value);
    }
}
