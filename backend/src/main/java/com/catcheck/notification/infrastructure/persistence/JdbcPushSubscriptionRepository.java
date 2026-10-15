package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.domain.PushPlatform;
import com.catcheck.notification.domain.PushRevokeReason;
import com.catcheck.notification.domain.PushSubscription;
import com.catcheck.notification.domain.port.PushSubscriptionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JdbcPushSubscriptionRepository implements PushSubscriptionRepository {

    private static final String COLUMNS = """
            id, user_id, fid, legacy_token, platform, device_label, user_agent, last_seen_at,
            last_success_at, last_error_at, failure_count, revoked_at, revoke_reason, created_at
            """;

    private final JdbcTemplate jdbc;

    JdbcPushSubscriptionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PushSubscription> findActiveByUser(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM push_subscription
                 WHERE user_id = ? AND revoked_at IS NULL
                 ORDER BY last_seen_at DESC, created_at DESC
                """, (rs, rowNum) -> read(rs), userId);
    }

    @Override
    public long countActiveByUser(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM push_subscription WHERE user_id = ? AND revoked_at IS NULL",
                Long.class, userId);
        return count == null ? 0L : count;
    }

    @Override
    public Optional<PushSubscription> findActiveByDeviceKey(UUID userId, String deviceKey) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM push_subscription
                 WHERE user_id = ? AND COALESCE(fid, legacy_token) = ? AND revoked_at IS NULL
                """, (rs, rowNum) -> read(rs), userId, deviceKey).stream().findFirst();
    }

    @Override
    public Optional<PushSubscription> findByIdForUser(UUID subscriptionId, UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + """
                  FROM push_subscription
                 WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                """, (rs, rowNum) -> read(rs), subscriptionId, userId).stream().findFirst();
    }

    @Override
    public PushSubscription upsert(PushSubscription subscription) {
        UUID id = jdbc.queryForObject("""
                INSERT INTO push_subscription (user_id, fid, legacy_token, platform, device_label,
                                               user_agent, last_seen_at)
                VALUES (?, ?, ?, ?, ?, ?, now())
                ON CONFLICT (user_id, COALESCE(fid, legacy_token)) DO UPDATE
                   SET legacy_token  = COALESCE(EXCLUDED.legacy_token, push_subscription.legacy_token),
                       platform      = EXCLUDED.platform,
                       device_label  = COALESCE(EXCLUDED.device_label, push_subscription.device_label),
                       user_agent    = COALESCE(EXCLUDED.user_agent, push_subscription.user_agent),
                       last_seen_at  = now(),
                       failure_count = 0,
                       revoked_at    = NULL,
                       revoke_reason = NULL
                RETURNING id
                """,
                UUID.class,
                subscription.userId(),
                subscription.installationId(),
                subscription.legacyToken(),
                subscription.platform().name(),
                subscription.deviceLabel(),
                subscription.userAgent());
        return jdbc.query("SELECT " + COLUMNS + " FROM push_subscription WHERE id = ?",
                        (rs, rowNum) -> read(rs), id)
                .stream().findFirst().orElseThrow();
    }

    @Override
    public int revokeInstallationIdOwnedByOtherUser(String installationId, UUID newOwnerId, Instant now) {
        return jdbc.update("""
                UPDATE push_subscription
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE fid = ? AND user_id <> ? AND revoked_at IS NULL
                """, NotificationRowReaders.offset(now), PushRevokeReason.USER_DISABLED.name(),
                installationId, newOwnerId);
    }

    @Override
    public int revokeLegacyTokenOwnedByOtherUser(String legacyToken, UUID newOwnerId, Instant now) {
        return jdbc.update("""
                UPDATE push_subscription
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE legacy_token = ? AND user_id <> ? AND revoked_at IS NULL
                """, NotificationRowReaders.offset(now), PushRevokeReason.USER_DISABLED.name(),
                legacyToken, newOwnerId);
    }

    @Override
    public boolean revoke(UUID subscriptionId, UUID userId, PushRevokeReason reason, Instant now) {
        return jdbc.update("""
                UPDATE push_subscription
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE id = ? AND user_id = ? AND revoked_at IS NULL
                """, NotificationRowReaders.offset(now), reason.name(), subscriptionId, userId) > 0;
    }

    @Override
    public void revokeById(UUID subscriptionId, PushRevokeReason reason, Instant now) {
        jdbc.update("""
                UPDATE push_subscription
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE id = ? AND revoked_at IS NULL
                """, NotificationRowReaders.offset(now), reason.name(), subscriptionId);
    }

    @Override
    public void recordSuccess(UUID subscriptionId, Instant now) {
        jdbc.update("""
                UPDATE push_subscription
                   SET last_success_at = ?, failure_count = 0
                 WHERE id = ?
                """, NotificationRowReaders.offset(now), subscriptionId);
    }

    @Override
    public int recordFailure(UUID subscriptionId, Instant now) {
        Integer updated = jdbc.queryForObject("""
                UPDATE push_subscription
                   SET last_error_at = ?, failure_count = failure_count + 1
                 WHERE id = ?
                RETURNING failure_count
                """, Integer.class, NotificationRowReaders.offset(now), subscriptionId);
        return updated == null ? 0 : updated;
    }


    @Override
    public long countAll() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM push_subscription", Long.class);
        return count == null ? 0L : count;
    }

    @Override
    public long countDead(Instant revokedBefore, Instant lastSeenBefore) {
        Long count = jdbc.queryForObject("""
                SELECT count(*) FROM push_subscription
                 WHERE (revoked_at IS NOT NULL AND revoked_at < ?)
                    OR last_seen_at < ?
                """, Long.class, revokedBefore, lastSeenBefore);
        return count == null ? 0L : count;
    }

    @Override
    public int deleteDead(Instant revokedBefore, Instant lastSeenBefore, int limit) {
        return jdbc.update("""
                DELETE FROM push_subscription
                 WHERE id IN (
                         SELECT id FROM push_subscription
                          WHERE (revoked_at IS NOT NULL AND revoked_at < ?)
                             OR last_seen_at < ?
                          ORDER BY created_at
                          LIMIT ?
                          FOR UPDATE SKIP LOCKED)
                """, revokedBefore, lastSeenBefore, limit);
    }

    private PushSubscription read(ResultSet rs) throws SQLException {
        String reason = rs.getString("revoke_reason");
        return new PushSubscription(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("fid"),
                rs.getString("legacy_token"),
                PushPlatform.valueOf(rs.getString("platform")),
                rs.getString("device_label"),
                rs.getString("user_agent"),
                NotificationRowReaders.instant(rs, "last_seen_at"),
                NotificationRowReaders.instant(rs, "last_success_at"),
                NotificationRowReaders.instant(rs, "last_error_at"),
                rs.getInt("failure_count"),
                NotificationRowReaders.instant(rs, "revoked_at"),
                reason == null ? null : PushRevokeReason.valueOf(reason),
                NotificationRowReaders.instant(rs, "created_at"));
    }
}
