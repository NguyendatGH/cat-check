package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record EmailOtpChallenge(
        UUID id,
        UUID userId,
        String email,
        String codeHash,
        int pepperVersion,
        OtpPurpose purpose,
        Instant expiresAt,
        int attemptCount,
        int maxAttempts,
        Instant consumedAt,
        String requestIp,
        Instant verifiedAt,
        String ticketHash,
        Instant ticketExpiresAt,
        Instant createdAt) {

    public boolean isUsableAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now) && attemptCount < maxAttempts;
    }

    public boolean hasAttemptsLeft() {
        return attemptCount < maxAttempts;
    }

    public boolean isTicketValidAt(Instant now) {
        return ticketHash != null && verifiedAt != null
                && consumedAt == null
                && ticketExpiresAt != null
                && ticketExpiresAt.isAfter(now);
    }
}
