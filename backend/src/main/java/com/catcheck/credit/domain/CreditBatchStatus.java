package com.catcheck.credit.domain;

import java.time.Instant;

public enum CreditBatchStatus {

    ACTIVE,

    EXHAUSTED,

    EXPIRED;

    public boolean isLive(Instant expiresAt, Instant now) {
        return this == ACTIVE && expiresAt.isAfter(now);
    }

    public static CreditBatchStatus closedBy(int remainingAmount) {
        return remainingAmount == 0 ? EXHAUSTED : ACTIVE;
    }
}
