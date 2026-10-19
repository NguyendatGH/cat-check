package com.catcheck.identity.api;

public enum CryptoPurpose {

    PII_PHONE("pii.phone"),

    PII_PUSH_TOKEN("pii.push_token"),

    MFA_TOTP("mfa.totp"),

    PII_LOCATION("pii.location");

    private final String label;

    CryptoPurpose(String label) {
        this.label = label;
    }

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
