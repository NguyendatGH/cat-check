package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần đẩy push tới MỘT thiết bị — bảng {@code notification_outbox} (p4 F5).
 *
 * <p>Tách khỏi {@code notification} vì một thông báo fan-out ra nhiều thiết bị: gộp chung sẽ
 * khiến inbox in-app hiển thị mỗi thiết bị một dòng (p4 F5 ghi chú nghiệp vụ).</p>
 *
 * @param lastErrorCode {@code UNREGISTERED}, {@code INVALID_ARGUMENT}… — đầu vào quyết định có
 *                      thu hồi {@code push_subscription} hay không (p12 §12.3.9)
 */
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
