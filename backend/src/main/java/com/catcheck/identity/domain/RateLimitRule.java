package com.catcheck.identity.domain;

public enum RateLimitRule {

    LOGIN_BY_EMAIL("5/1m", KeyKind.EMAIL),

    LOGIN_BY_IP("10/1m", KeyKind.IP),

    OTP_REQUEST_BURST("1/1m", KeyKind.EMAIL_PURPOSE),

    OTP_REQUEST_HOURLY("5/1h", KeyKind.EMAIL_PURPOSE),

    OTP_REQUEST_DAILY("10/24h", KeyKind.EMAIL_PURPOSE),

    OTP_REQUEST_BY_IP("20/1h", KeyKind.IP),

    OTP_VERIFY("10/1h", KeyKind.EMAIL_PURPOSE),

    PASSWORD_RESET_BY_EMAIL("3/1h", KeyKind.EMAIL),

    PASSWORD_RESET_BY_IP("10/1h", KeyKind.IP),

    ACCOUNT_PASSWORD("5/1h", KeyKind.USER_ID),


    MFA_VERIFY("5/5m", KeyKind.USER_ID),

    MFA_RECOVERY("5/1h", KeyKind.USER_ID),

    MFA_INIT("5/1h", KeyKind.USER_ID),

    MFA_CONFIRM("5/5m", KeyKind.USER_ID),

    MFA_REGENERATE("5/1h", KeyKind.USER_ID);

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

    public String documentedRate() {
        return documentedRate;
    }

    public KeyKind keyKind() {
        return keyKind;
    }

    public String bucketNamespace() {
        return name();
    }
}
