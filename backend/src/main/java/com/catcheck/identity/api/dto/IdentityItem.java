package com.catcheck.identity.api.dto;

import java.time.Instant;

public record IdentityItem(
        String provider,
        String providerEmail,
        boolean emailVerified,
        Instant lastUsedAt) {
}
