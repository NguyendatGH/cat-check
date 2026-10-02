package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Một phiên đăng nhập trong {@link SessionListResponse} (p8 §8.4.4 nhóm A). */
public record SessionItem(
        UUID id,
        String deviceLabel,
        String ipMasked,
        Instant lastSeenAt,
        Instant createdAt,
        boolean current) {
}
