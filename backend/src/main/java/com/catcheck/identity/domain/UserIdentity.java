package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record UserIdentity(
        UUID id,
        UUID userId,
        IdentityProvider provider,
        String providerUserId,
        String providerEmail,
        boolean emailVerified,
        String passwordHash,
        Instant linkedAt,
        Instant lastUsedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    public static String bcryptPrefix() {
        return "{bcrypt}";
    }
}
