package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.domain.port.NotificationOutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bảng {@code notification_outbox} (V13, p4 F5) — giao vận push. */
@Repository
class JdbcNotificationOutboxRepository implements NotificationOutboxRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    JdbcNotificationOutboxRepository(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void insertIfAbsent(UUID notificationId, UUID pushSubscriptionId, Instant nextAttemptAt) {
        jdbc.update("""
                INSERT INTO notification_outbox (notification_id, push_subscription_id, status,
                                                 attempts, next_attempt_at)
                VALUES (?, ?, 'PENDING', 0, ?)
                ON CONFLICT (notification_id, push_subscription_id) DO NOTHING
                """, notificationId, pushSubscriptionId, NotificationRowReaders.offset(nextAttemptAt));
    }

    /**
     * Join sẵn {@code push_subscription} (lọc {@code revoked_at IS NULL} — thiết bị đã thu hồi
     * giữa lúc xếp hàng và lúc gửi thì bỏ qua) và {@code notification} (lấy nội dung đã render).
     */
    @Override
    public List<PendingPush> claimPending(Instant now, int limit) {
        return jdbc.query("""
                SELECT o.id            AS outbox_id,
                       o.notification_id,
                       o.push_subscription_id,
                       o.attempts,
                       s.fid,
                       s.legacy_token,
                       n.template_code,
                       n.title_snapshot,
                       n.body_snapshot,
                       n.payload
                  FROM notification_outbox o
                  JOIN push_subscription s ON s.id = o.push_subscription_id
                  JOIN notification n      ON n.id = o.notification_id
                 WHERE o.status = 'PENDING'
                   AND o.next_attempt_at <= ?
                   AND s.revoked_at IS NULL
                 ORDER BY o.next_attempt_at
                 LIMIT ?
                   FOR UPDATE OF o SKIP LOCKED
                """, (rs, rowNum) -> new PendingPush(
                        rs.getObject("outbox_id", UUID.class),
                        rs.getObject("notification_id", UUID.class),
                        rs.getObject("push_subscription_id", UUID.class),
                        rs.getInt("attempts"),
                        rs.getString("fid"),
                        rs.getString("legacy_token"),
                        rs.getString("template_code"),
                        rs.getString("title_snapshot"),
                        rs.getString("body_snapshot"),
                        NotificationRowReaders.json(jsonMapper, rs.getString("payload"))),
                NotificationRowReaders.offset(now), limit);
    }

    @Override
    public void markSent(UUID id, String fcmMessageId, Instant sentAt) {
        jdbc.update("""
                UPDATE notification_outbox
                   SET status = 'SENT', fcm_message_id = ?, sent_at = ?, attempts = attempts + 1,
                       last_error_code = NULL, last_error = NULL
                 WHERE id = ?
                """, fcmMessageId, NotificationRowReaders.offset(sentAt), id);
    }

    @Override
    public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String errorCode, String lastError) {
        jdbc.update("""
                UPDATE notification_outbox
                   SET attempts = ?, next_attempt_at = ?, last_error_code = ?, last_error = ?
                 WHERE id = ?
                """, attempts, NotificationRowReaders.offset(nextAttemptAt), errorCode, lastError, id);
    }

    @Override
    public void markFailed(UUID id, int attempts, String errorCode, String lastError) {
        jdbc.update("""
                UPDATE notification_outbox
                   SET status = 'FAILED', attempts = ?, last_error_code = ?, last_error = ?
                 WHERE id = ?
                """, attempts, errorCode, lastError, id);
    }

    @Override
    public boolean requeueFailed(UUID id, Instant now) {
        return jdbc.update("""
                UPDATE notification_outbox
                   SET status = 'PENDING', attempts = 0, next_attempt_at = ?,
                       last_error = NULL, last_error_code = NULL
                 WHERE id = ? AND status = 'FAILED'
                """, NotificationRowReaders.offset(now), id) == 1;
    }

    @Override
    public java.util.Optional<String> findStatus(UUID id) {
        return jdbc.query("SELECT status FROM notification_outbox WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), id).stream().findFirst();
    }
}
