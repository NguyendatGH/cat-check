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
     * L67 — như {@code EmailOutboxRepository.requeueFailed}: chỉ đổi dòng đang {@code FAILED},
     * điều kiện nằm trong chính câu {@code UPDATE} để không có cuộc đua với
     * {@code RetryFailedNotificationsJob}.
     *
     * <p>Xoá luôn {@code last_error_code}: giữ lại một mã như {@code UNREGISTERED} trên một dòng
     * vừa được đưa lại hàng chờ sẽ khiến bộ lọc "thiết bị cần thu hồi" của
     * {@code PushOutboxDispatcher} đọc sai trạng thái.
     *
     * @return {@code true} nếu có đúng một dòng được đưa lại hàng chờ
     */
    boolean requeueFailed(UUID id, Instant now);

    /** Trạng thái hiện tại của một dòng; rỗng nếu không có dòng nào mang id này. */
    java.util.Optional<String> findStatus(UUID id);

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
