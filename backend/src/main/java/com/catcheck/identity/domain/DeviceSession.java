package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record DeviceSession(
        UUID id,
        UUID userId,
        String sessionIdHash,
        String deviceLabel,
        String userAgent,
        String ipAddress,
        boolean rememberMe,
        Instant createdAt,
        Instant lastSeenAt,
        Instant expiresAt,
        Instant revokedAt,
        SessionRevokeReason revokeReason) {

    public boolean isActiveAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isCurrent(String currentSessionIdHash) {
        return sessionIdHash.equals(currentSessionIdHash);
    }
}
