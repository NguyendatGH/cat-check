package com.catcheck.notification.api;

import java.util.Map;
import java.util.UUID;

public record NotificationRequest(
        UUID userId,
        String templateCode,
        Map<String, Object> payload,
        String title,
        String body,
        String refType,
        UUID refId,
        String dedupeKey) {

    public NotificationRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static NotificationRequest of(UUID userId, String templateCode, String title, String body,
                                         Map<String, Object> payload) {
        return new NotificationRequest(userId, templateCode, payload, title, body, null, null, null);
    }
}
