package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.domain.Notification;
import com.catcheck.notification.domain.NotificationChannel;
import com.catcheck.notification.domain.NotificationRefType;
import com.catcheck.notification.domain.NotificationStatus;
import com.catcheck.notification.domain.NotificationTemplate;
import com.catcheck.notification.domain.port.NotificationRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcNotificationRepository implements NotificationRepository {

    private static final String COLUMNS = """
            id, user_id, channel, template_code, payload, title_snapshot, body_snapshot, status,
            ref_type, ref_id, dedupe_key, attempt_count, scheduled_at, sent_at, read_at, created_at
            """;

    private static final String INBOX_FILTER = """
             WHERE user_id = ?
               AND channel = 'IN_APP'
               AND status = 'SENT'
               AND payload->>'hiddenAt' IS NULL
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    JdbcNotificationRepository(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<Notification> insertIfAbsent(Notification notification) {
        List<UUID> inserted = jdbc.query("""
                INSERT INTO notification (user_id, channel, template_code, payload, title_snapshot,
                                          body_snapshot, status, ref_type, ref_id, dedupe_key,
                                          scheduled_at, sent_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING
                RETURNING id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                notification.userId(),
                notification.channel().name(),
                notification.template().code(),
                NotificationRowReaders.toJson(jsonMapper, notification.payload()),
                notification.titleSnapshot(),
                notification.bodySnapshot(),
                notification.status().name(),
                notification.refType() == null ? null : notification.refType().name(),
                notification.refId(),
                notification.dedupeKey(),
                NotificationRowReaders.offset(notification.scheduledAt()),
                NotificationRowReaders.offset(notification.sentAt()));
        if (inserted.isEmpty()) {
            return Optional.empty();
        }
        return findById(inserted.get(0));
    }

    @Override
    public Page listInbox(UUID userId, InboxCursor cursor, int limit) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM notification" + INBOX_FILTER);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        if (cursor != null) {
            sql.append(" AND (created_at, id) < (?, ?)");
            args.add(NotificationRowReaders.offset(cursor.createdAt()));
            args.add(cursor.id());
        }
        sql.append(" ORDER BY created_at DESC, id DESC LIMIT ?");
        args.add(limit + 1);

        List<Notification> fetched = jdbc.query(sql.toString(), (rs, rowNum) -> read(rs), args.toArray())
                .stream().filter(java.util.Objects::nonNull).toList();
        boolean hasMore = fetched.size() > limit;
        List<Notification> items = hasMore ? List.copyOf(fetched.subList(0, limit)) : fetched;
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            Notification last = items.get(items.size() - 1);
            nextCursor = new InboxCursor(last.createdAt(), last.id()).encode();
        }
        return new Page(items, nextCursor, hasMore);
    }

    @Override
    public long countUnread(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM notification" + INBOX_FILTER + " AND read_at IS NULL",
                Long.class, userId);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<Notification> findByIdForUser(UUID notificationId, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM notification
                 WHERE id = ? AND user_id = ? AND channel = 'IN_APP'
                """, (rs, rowNum) -> read(rs), notificationId, userId)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    @Override
    public boolean markRead(UUID notificationId, UUID userId) {
        return jdbc.update("""
                UPDATE notification SET read_at = now()
                 WHERE id = ? AND user_id = ? AND channel = 'IN_APP' AND read_at IS NULL
                """, notificationId, userId) > 0;
    }

    @Override
    public int markAllRead(UUID userId) {
        return jdbc.update("""
                UPDATE notification SET read_at = now()
                 WHERE user_id = ? AND channel = 'IN_APP' AND read_at IS NULL
                   AND status = 'SENT' AND payload->>'hiddenAt' IS NULL
                """, userId);
    }

    @Override
    public boolean hide(UUID notificationId, UUID userId) {
        return jdbc.update("""
                UPDATE notification
                   SET payload = jsonb_set(payload, '{hiddenAt}', to_jsonb(now()::text), true)
                 WHERE id = ? AND user_id = ? AND channel = 'IN_APP'
                """, notificationId, userId) > 0;
    }

    @Override
    public void updateStatus(UUID notificationId, String status, String lastError) {
        jdbc.update("""
                UPDATE notification
                   SET status = ?,
                       last_error = ?,
                       attempt_count = attempt_count + 1,
                       sent_at = CASE WHEN ? = 'SENT' THEN now() ELSE sent_at END
                 WHERE id = ?
                """, status, lastError, status, notificationId);
    }

    private Optional<Notification> findById(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM notification WHERE id = ?",
                        (rs, rowNum) -> read(rs), id)
                .stream().filter(java.util.Objects::nonNull).findFirst();
    }

    private Notification read(ResultSet rs) throws SQLException {
        Optional<NotificationTemplate> template =
                NotificationTemplate.fromCode(rs.getString("template_code"));
        if (template.isEmpty()) {
            return null;
        }
        String refType = rs.getString("ref_type");
        return new Notification(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                NotificationChannel.valueOf(rs.getString("channel")),
                template.get(),
                NotificationRowReaders.json(jsonMapper, rs.getString("payload")),
                rs.getString("title_snapshot"),
                rs.getString("body_snapshot"),
                NotificationStatus.valueOf(rs.getString("status")),
                refType == null ? null : NotificationRefType.valueOf(refType),
                rs.getObject("ref_id", UUID.class),
                rs.getString("dedupe_key"),
                rs.getInt("attempt_count"),
                NotificationRowReaders.instant(rs, "scheduled_at"),
                NotificationRowReaders.instant(rs, "sent_at"),
                NotificationRowReaders.instant(rs, "read_at"),
                NotificationRowReaders.instant(rs, "created_at"));
    }
}
