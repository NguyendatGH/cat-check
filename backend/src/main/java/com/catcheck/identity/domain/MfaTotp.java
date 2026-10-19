package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record MfaTotp(
        UUID userId,
        byte[] secretEnc,
        TotpStatus status,
        String algorithm,
        int digits,
        int periodSeconds,
        Long lastUsedStep,
        int failedCount,
        Instant lockedUntil,
        Instant pendingExpiresAt,
        Instant activatedAt,
        int keyVersion,
        UUID resetBy,
        Instant resetAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isActive() {
        return status == TotpStatus.ACTIVE;
    }
}
