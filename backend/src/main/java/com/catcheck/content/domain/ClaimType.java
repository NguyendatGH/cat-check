package com.catcheck.content.domain;

public enum ClaimType {

    NONE,

    MEDICAL,

    STATISTIC,

    CERTIFICATION,

    PERFORMANCE,

    SERVICE_COMMITMENT;

    public boolean requiresSourceReference() {
        return this != NONE;
    }
}
