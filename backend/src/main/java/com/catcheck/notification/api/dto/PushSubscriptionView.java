package com.catcheck.notification.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PushSubscriptionView(
        UUID id,
        String platform,
        String deviceLabel,
        Instant lastSeenAt,
        Instant lastSuccessAt,
        Instant createdAt) {
}
