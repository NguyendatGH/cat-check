package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record PushSubscription(
        UUID id,
        UUID userId,
        String installationId,
        String legacyToken,
        PushPlatform platform,
        String deviceLabel,
        String userAgent,
        Instant lastSeenAt,
        Instant lastSuccessAt,
        Instant lastErrorAt,
        int failureCount,
        Instant revokedAt,
        PushRevokeReason revokeReason,
        Instant createdAt) {

    public PushSubscription {
        if (installationId == null && legacyToken == null) {
            throw new IllegalArgumentException(
                    "push_subscription phải có ít nhất fid hoặc legacy_token (ck_push_identifier)");
        }
    }

    public boolean active() {
        return revokedAt == null;
    }

    public String deviceKey() {
        return installationId != null ? installationId : legacyToken;
    }

    public boolean usesInstallationId() {
        return installationId != null;
    }
}
