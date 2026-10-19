package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Notification(
        UUID id,
        UUID userId,
        NotificationChannel channel,
        NotificationTemplate template,
        Map<String, Object> payload,
        String titleSnapshot,
        String bodySnapshot,
        NotificationStatus status,
        NotificationRefType refType,
        UUID refId,
        String dedupeKey,
        int attemptCount,
        Instant scheduledAt,
        Instant sentAt,
        Instant readAt,
        Instant createdAt) {

    public static final String HIDDEN_AT_KEY = "hiddenAt";

    public static final String SUPPRESSED_REASON_KEY = "suppressedReason";

    public Notification {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public boolean unread() {
        return readAt == null;
    }

    public boolean hidden() {
        return payload.get(HIDDEN_AT_KEY) != null;
    }
}
