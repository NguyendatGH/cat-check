package com.catcheck.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationView(
        UUID id,
        String templateCode,
        String title,
        String body,
        String deepLink,
        String refType,
        UUID refId,
        boolean read,
        Instant readAt,
        Instant createdAt) {
}
