package com.catcheck.credit.domain;

public enum ActivationRateLimitRule {

    ACTIVATION_BY_USER("5/15m", KeyKind.USER_ID),

    ACTIVATION_BY_IP("20/1h", KeyKind.IP);

    public enum KeyKind {
        USER_ID,
        IP
    }

    private final String documentedRate;
    private final KeyKind keyKind;

    ActivationRateLimitRule(String documentedRate, KeyKind keyKind) {
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
