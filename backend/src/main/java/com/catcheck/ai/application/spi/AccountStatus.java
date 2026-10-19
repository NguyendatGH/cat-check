package com.catcheck.ai.application.spi;

public enum AccountStatus {

    PENDING_VERIFICATION,
    ACTIVE,
    LOCKED,
    RESTRICTED,
    DELETION_REQUESTED,
    ANONYMIZED;

    public static AccountStatus fromWire(String value) {
        if (value == null) {
            throw new IllegalArgumentException("account status không được null");
        }
        for (AccountStatus candidate : values()) {
            if (candidate.name().equals(value)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("account status không hợp lệ: " + value);
    }

    public boolean allowsWrite() {
        return this == ACTIVE;
    }
}
