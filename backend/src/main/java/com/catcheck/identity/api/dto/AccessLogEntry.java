package com.catcheck.identity.api.dto;

import java.time.Instant;

public record AccessLogEntry(
        Instant occurredAt,
        String actorType,
        String action,
        String targetType,
        String reason) {
}
