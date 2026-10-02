package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link DeviceSessionRepository} tren {@code JdbcTemplate}.
 *
 * <p>Bang nay chi la <b>ban sao</b> cua phien trong {@code SPRING_SESSION}, dung de
 * hien thi cho nguoi dung. Xac thuc van la Spring Session; khi thu hoi phien phai
 * goi ca {@code AuthenticatedSessionRevoker} (noi that) va bang nay (hien thi).</p>
 */
@Repository
public class JdbcDeviceSessionRepository implements DeviceSessionRepository {

    private static final String COLUMNS = """
            id, user_id, session_id_hash, device_label, user_agent, ip_address,
            remember_me, created_at, last_seen_at, expires_at, revoked_at, revoke_reason
            """;

    private static final String INSERT = """
            INSERT INTO user_device_session (
                id, user_id, session_id_hash, device_label, user_agent, ip_address,
                remember_me, created_at, last_seen_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcDeviceSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<DeviceSession> findActiveByUserId(UUID userId, Instant now) {
        return jdbc.query("""
                SELECT %s FROM user_device_session
                 WHERE user_id = ? AND revoked_at IS NULL AND expires_at > ?
                 ORDER BY last_seen_at DESC
                """.formatted(COLUMNS), (rs, rowNum) -> map(rs), userId, utc(now));
    }

    @Override
    public Optional<DeviceSession> findById(UUID sessionId) {
        return one("SELECT " + COLUMNS + " FROM user_device_session WHERE id = ?", sessionId);
    }

    @Override
    public Optional<DeviceSession> findBySessionIdHash(String sessionIdHash) {
        return one("SELECT " + COLUMNS + " FROM user_device_session WHERE session_id_hash = ?", sessionIdHash);
    }

    @Override
    public void insert(DeviceSession session) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, session.id());
            ps.setObject(i++, session.userId());
            ps.setString(i++, session.sessionIdHash());
            ps.setString(i++, session.deviceLabel());
            ps.setString(i++, session.userAgent());
            if (session.ipAddress() == null) {
                ps.setNull(i++, Types.VARCHAR);
            } else {
                ps.setString(i++, session.ipAddress());
            }
            ps.setBoolean(i++, session.rememberMe());
            ps.setObject(i++, utc(session.createdAt()));
            ps.setObject(i++, utc(session.lastSeenAt()));
            ps.setObject(i, utc(session.expiresAt()));
            return ps;
        });
    }

    @Override
    public void touchLastSeen(UUID sessionId, Instant lastSeenAt) {
        jdbc.update("UPDATE user_device_session SET last_seen_at = ? WHERE id = ?", utc(lastSeenAt), sessionId);
    }

    @Override
    public boolean revoke(UUID sessionId, Instant revokedAt, SessionRevokeReason reason) {
        // `AND revoked_at IS NULL` de thao tac idempotent: bam nut hai lan khong loi,
        // va lan hai khong ghi deo ly do goc.
        return jdbc.update("""
                UPDATE user_device_session
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE id = ? AND revoked_at IS NULL
                """, utc(revokedAt), reason.name(), sessionId) > 0;
    }

    @Override
    public int revokeAllByUserId(UUID userId, Instant revokedAt, SessionRevokeReason reason) {
        return jdbc.update("""
                UPDATE user_device_session
                   SET revoked_at = ?, revoke_reason = ?
                 WHERE user_id = ? AND revoked_at IS NULL
                """, utc(revokedAt), reason.name(), userId);
    }

    private Optional<DeviceSession> one(String sql, Object... args) {
        List<DeviceSession> rows = jdbc.query(sql, (rs, rowNum) -> map(rs), args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private DeviceSession map(ResultSet rs) throws SQLException {
        return new DeviceSession(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "user_id"),
                RowReaders.text(rs, "session_id_hash"),
                RowReaders.textOrNull(rs, "device_label"),
                RowReaders.textOrNull(rs, "user_agent"),
                RowReaders.textOrNull(rs, "ip_address"),
                RowReaders.bool(rs, "remember_me"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.requiredInstant(rs, "last_seen_at"),
                RowReaders.requiredInstant(rs, "expires_at"),
                RowReaders.instant(rs, "revoked_at"),
                RowReaders.enumValue(rs, "revoke_reason", SessionRevokeReason.class));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
