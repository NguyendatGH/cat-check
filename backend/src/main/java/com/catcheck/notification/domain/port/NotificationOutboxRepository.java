package com.catcheck.notification.domain.port;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

public interface NotificationOutboxRepository {

    void insertIfAbsent(UUID notificationId, UUID pushSubscriptionId, Instant nextAttemptAt);

    List<PendingPush> claimPending(Instant now, int limit);

    void markSent(UUID id, String fcmMessageId, Instant sentAt);

    void markRetry(UUID id, int attempts, Instant nextAttemptAt, String errorCode, String lastError);

    void markFailed(UUID id, int attempts, String errorCode, String lastError);

    boolean requeueFailed(UUID id, Instant now);

    java.util.Optional<String> findStatus(UUID id);

    record PendingPush(
            UUID outboxId,
            UUID notificationId,
            UUID pushSubscriptionId,
            int attempts,
            String installationId,
            String legacyToken,
            String templateCode,
            String title,
            String body,
            Map<String, Object> payload) {

        public PendingPush {
            payload = payload == null ? Map.of() : Map.copyOf(payload);
        }
    }
}
