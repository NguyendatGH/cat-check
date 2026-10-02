package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.TotpStatus;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link MfaTotpRepository} tren {@code JdbcTemplate}.
 *
 * <p>{@code secret_enc} la ciphertext AES-256-GCM — repository tra ve nguyen vien,
 * giai ma chi xay ra o tang application khi can verify ma (p4 §A7: secret khong bao
 * gio vao {@code audit_log.before/after}).</p>
 */
@Repository
public class JdbcMfaTotpRepository implements MfaTotpRepository {

    private static final String COLUMNS = """
            user_id, secret_enc, status, algorithm, digits, period_seconds,
            last_used_step, failed_count, locked_until, pending_expires_at,
            activated_at, key_version, reset_by, reset_at, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcMfaTotpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MfaTotp> findByUserId(UUID userId) {
        var rows = jdbc.query("SELECT " + COLUMNS + " FROM user_mfa_totp WHERE user_id = ?",
                (rs, rowNum) -> map(rs), userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public void insert(MfaTotp totp) {
        jdbc.update("""
                INSERT INTO user_mfa_totp (
                    user_id, secret_enc, status, algorithm, digits, period_seconds,
                    last_used_step, failed_count, locked_until, pending_expires_at,
                    activated_at, key_version, reset_by, reset_at, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                totp.userId(), totp.secretEnc(), totp.status().name(), totp.algorithm(),
                totp.digits(), totp.periodSeconds(), totp.lastUsedStep(), totp.failedCount(),
                utc(totp.lockedUntil()), utc(totp.pendingExpiresAt()), utc(totp.activatedAt()),
                totp.keyVersion(), totp.resetBy(), utc(totp.resetAt()),
                utc(totp.createdAt()), utc(totp.updatedAt()));
    }

    @Override
    public void markActive(UUID userId, Instant activatedAt, long lastUsedStep) {
        jdbc.update("""
                UPDATE user_mfa_totp
                   SET status = ?, activated_at = ?, last_used_step = ?, failed_count = 0
                 WHERE user_id = ?
                """, TotpStatus.ACTIVE.name(), utc(activatedAt), lastUsedStep, userId);
    }

    @Override
    public void updateLastUsedStep(UUID userId, long step) {
        jdbc.update("UPDATE user_mfa_totp SET last_used_step = ? WHERE user_id = ?", step, userId);
    }

    @Override
    public int incrementFailedCount(UUID userId) {
        Integer after = jdbc.queryForObject(
                "UPDATE user_mfa_totp SET failed_count = failed_count + 1 WHERE user_id = ? RETURNING failed_count",
                Integer.class, userId);
        return after == null ? 0 : after;
    }

    @Override
    public void resetFailedCount(UUID userId) {
        jdbc.update("UPDATE user_mfa_totp SET failed_count = 0, locked_until = NULL WHERE user_id = ?", userId);
    }

    @Override
    public void lock(UUID userId, Instant lockedUntil) {
        jdbc.update("UPDATE user_mfa_totp SET locked_until = ? WHERE user_id = ?", utc(lockedUntil), userId);
    }

    @Override
    public void delete(UUID userId) {
        jdbc.update("DELETE FROM user_mfa_totp WHERE user_id = ?", userId);
    }

    @Override
    public long countActive() {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_mfa_totp WHERE status = ?", Long.class, TotpStatus.ACTIVE.name());
        return count == null ? 0 : count;
    }

    private MfaTotp map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new MfaTotp(
                RowReaders.uuid(rs, "user_id"),
                rs.getBytes("secret_enc"),
                RowReaders.requiredEnum(rs, "status", TotpStatus.class),
                RowReaders.text(rs, "algorithm"),
                RowReaders.requiredInt(rs, "digits"),
                RowReaders.requiredInt(rs, "period_seconds"),
                RowReaders.longOrNull(rs, "last_used_step"),
                RowReaders.requiredInt(rs, "failed_count"),
                RowReaders.instant(rs, "locked_until"),
                RowReaders.instant(rs, "pending_expires_at"),
                RowReaders.instant(rs, "activated_at"),
                RowReaders.requiredInt(rs, "key_version"),
                RowReaders.uuidOrNull(rs, "reset_by"),
                RowReaders.instant(rs, "reset_at"),
                RowReaders.requiredInstant(rs, "created_at"),
                RowReaders.requiredInstant(rs, "updated_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
