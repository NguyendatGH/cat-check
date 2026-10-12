package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.application.spi.NotificationRecipientPort;
import com.catcheck.notification.domain.NotificationRecipient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcNotificationRecipientAdapter implements NotificationRecipientPort {

    private final JdbcTemplate jdbc;

    JdbcNotificationRecipientAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<NotificationRecipient> findById(UUID userId) {
        return jdbc.query("""
                SELECT id, email, locale, timezone
                  FROM app_user
                 WHERE id = ? AND status NOT IN ('ANONYMIZED', 'DELETION_REQUESTED')
                """, (rs, rowNum) -> new NotificationRecipient(
                        rs.getObject("id", UUID.class),
                        rs.getString("email"),
                        rs.getString("locale"),
                        zone(rs.getString("timezone"))),
                userId).stream().findFirst();
    }

    @Override
    public List<UUID> findActiveRecipientIdsAfter(UUID afterId, int limit) {
        return jdbc.query("""
                SELECT id
                  FROM app_user
                 WHERE status NOT IN ('ANONYMIZED', 'DELETION_REQUESTED')
                   AND (CAST(? AS uuid) IS NULL OR id > CAST(? AS uuid))
                 ORDER BY id
                 LIMIT ?
                """, (rs, rowNum) -> rs.getObject("id", UUID.class), afterId, afterId, limit);
    }

    private ZoneId zone(String raw) {
        if (raw == null || raw.isBlank()) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
        try {
            return ZoneId.of(raw);
        } catch (DateTimeException ex) {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }
    }
}
