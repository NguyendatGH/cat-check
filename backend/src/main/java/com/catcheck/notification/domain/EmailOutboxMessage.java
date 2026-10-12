package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record EmailOutboxMessage(
        UUID id,
        String toAddress,
        String templateCode,
        String locale,
        Map<String, Object> payload,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        String dedupeKey,
        Instant sentAt,
        Instant createdAt) {

    public EmailOutboxMessage {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
