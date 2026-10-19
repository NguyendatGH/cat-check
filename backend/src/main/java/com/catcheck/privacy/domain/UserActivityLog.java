package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UserActivityLog(
        UUID id,
        UUID userId,
        String eventCode,
        Map<String, Object> props,
        Instant occurredAt,
        Instant createdAt
) {

    public UserActivityLog {
        if (id == null || eventCode == null || occurredAt == null) {
            throw new IllegalArgumentException("userActivityLog thiếu trường bắt buộc");
        }
        props = props == null ? Map.of() : Map.copyOf(props);
    }
}
