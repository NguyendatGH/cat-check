package com.catcheck.identity.api.dto;

import java.time.Instant;

/** Một identity liên kết trong {@link IdentityListResponse} (p8 §8.4.4 nhóm B). */
public record IdentityItem(
        String provider,
        String providerEmail,
        boolean emailVerified,
        Instant lastUsedAt) {
}
