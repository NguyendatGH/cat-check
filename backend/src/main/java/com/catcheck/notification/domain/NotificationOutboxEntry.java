package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.UUID;

public record NotificationOutboxEntry(
        UUID id,
        UUID notificationId,
        UUID pushSubscriptionId,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String fcmMessageId,
        String lastErrorCode,
        String lastError,
        Instant sentAt,
        Instant createdAt) {
}
