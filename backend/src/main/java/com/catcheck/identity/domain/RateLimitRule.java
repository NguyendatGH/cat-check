package com.catcheck.identity.domain;

/**
 * Danh muc quy tac gioi han trong module identity, dung tuyet doi so voi bang
 * {@code 11.7.2} cua p11.
 *
 * <p>Moi quy tac ghi ro muc do, ky tu, va {@link #keyKind} — p11 §11.7.1 bat buoc
 * khoa uu tien theo {@code email}/{@code userId}, IP chi la <b>lop phu</b>, vi
 * CGNAT o Viet Nam mot IPv4 phuc vu hang nghin thue bao.</p>
 */
public enum RateLimitRule {

    /** p11 §11.7.2: {@code POST /auth/login} — 5/phut theo email chuan hoa. */
    LOGIN_BY_EMAIL("5/1m", KeyKind.EMAIL),

    /** p11 §11.7.2: {@code POST /auth/login} — 10/phut theo IP. Lop phu. */
    LOGIN_BY_IP("10/1m", KeyKind.IP),

    /** p11 §11.7.2: {@code POST /auth/otp/request} — 1/60giay theo email + purpose. */
    OTP_REQUEST_BURST("1/1m", KeyKind.EMAIL_PURPOSE),

    /** p11 §11.7.2: {@code POST /auth/otp/request} — 5/giay theo email + purpose. */
    OTP_REQUEST_HOURLY("5/1h", KeyKind.EMAIL_PURPOSE),

    /** p11 §11.7.2: {@code POST /auth/otp/request} — 10/ngay theo email + purpose. */
    OTP_REQUEST_DAILY("10/24h", KeyKind.EMAIL_PURPOSE),

    /** p11 §11.7.2: {@code POST /auth/otp/request} — 20/giay theo IP. Lop phu. */
    OTP_REQUEST_BY_IP("20/1h", KeyKind.IP),

    /**
     * p11 §11.7.2: {@code POST /auth/otp/verify} — 10/giay theo email + purpose.
     * <b>Doc lap</b> voi bo dem 5 lan sai/challenge: bo dem challenge giet ma, rate
     * limit giet luong.
     */
    OTP_VERIFY("10/1h", KeyKind.EMAIL_PURPOSE),

    /** p11 §11.7.2: {@code POST /auth/password-reset/request} — 3/giay theo email. */
    PASSWORD_RESET_BY_EMAIL("3/1h", KeyKind.EMAIL),

    /** p11 §11.7.2: {@code POST /auth/password-reset/request} — 10/giay theo IP. */
    PASSWORD_RESET_BY_IP("10/1h", KeyKind.IP),

    /** p11 §11.7.2: {@code POST /account/password} — 5/giay theo userId. */
    ACCOUNT_PASSWORD("5/1h", KeyKind.USER_ID),

    /* --- MFA / TOTP (p11 §11.12.3) --- */

    /** p11 §11.12.3: {@code POST /auth/totp/verify} — 5/5 phut theo userId. */
    MFA_VERIFY("5/5m", KeyKind.USER_ID),

    /** p11 §11.12.3: {@code POST /auth/totp/recovery} — 5/giay theo userId. */
    MFA_RECOVERY("5/1h", KeyKind.USER_ID),

    /** p11 §11.12.3: {@code POST /account/mfa/totp/init} — 5/giay theo userId. */
    MFA_INIT("5/1h", KeyKind.USER_ID),

    /** p11 §11.12.3: {@code POST /account/mfa/totp/confirm} — 5/5 phut theo userId. */
    MFA_CONFIRM("5/5m", KeyKind.USER_ID),

    /** p11 §11.12.3: {@code POST /account/mfa/totp/recovery-codes/regenerate} — 5/giay. */
    MFA_REGENERATE("5/1h", KeyKind.USER_ID);

    /** Loai khoa dung de dem. */
    public enum KeyKind {
        EMAIL,
        EMAIL_PURPOSE,
        USER_ID,
        IP
    }

    private final String documentedRate;
    private final KeyKind keyKind;

    RateLimitRule(String documentedRate, KeyKind keyKind) {
        this.documentedRate = documentedRate;
        this.keyKind = keyKind;
    }

    /** Chuoi muc do nguy ban tu p11 §11.7.2, dung cho log va test. */
    public String documentedRate() {
        return documentedRate;
    }

    public KeyKind keyKind() {
        return keyKind;
    }

    /** Goc cua khoa dem — tach theo quy tac de {@code OTP_REQUEST_*} khong chia se thung. */
    public String bucketNamespace() {
        return name();
    }
}
