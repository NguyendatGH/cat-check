package com.catcheck.identity.api.dto;

import java.time.Instant;

/** Một dòng trong {@link AccessLogResponse} (p8 §8.4.4 nhóm B, B13). */
public record AccessLogEntry(
        Instant occurredAt,
        String actorType,
        String action,
        String targetType,
        String reason) {
}
