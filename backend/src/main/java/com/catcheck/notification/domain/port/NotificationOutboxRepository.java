package com.catcheck.notification.domain.port;

import java.time.Instant;
import java.util.Map;
import java.util.List;
import java.util.UUID;

/** Bảng {@code notification_outbox} (p4 F5) — giao vận push. */
public interface NotificationOutboxRepository {

    /** Va {@code uq_notification_outbox_target} ⇒ bỏ qua (một thông báo gửi tối đa một lần/thiết bị). */
    void insertIfAbsent(UUID notificationId, UUID pushSubscriptionId, Instant nextAttemptAt);

    /** {@code FOR UPDATE SKIP LOCKED}, kèm sẵn đích gửi để job không phải query thêm. */
    List<PendingPush> claimPending(Instant now, int limit);

    void markSent(UUID id, String fcmMessageId, Instant sentAt);

    void markRetry(UUID id, int attempts, Instant nextAttemptAt, String errorCode, String lastError);

    void markFailed(UUID id, int attempts, String errorCode, String lastError);

    /**
     * Một dòng outbox đã join sẵn với {@code push_subscription} và {@code notification}.
     *
     * @param installationId {@code fid}; null ⇒ dùng {@code legacyToken}
     */
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
