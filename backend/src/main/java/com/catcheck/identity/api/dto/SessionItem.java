package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionItem(
        UUID id,
        String deviceLabel,
        String ipMasked,
        Instant lastSeenAt,
        Instant createdAt,
        boolean current) {
}
