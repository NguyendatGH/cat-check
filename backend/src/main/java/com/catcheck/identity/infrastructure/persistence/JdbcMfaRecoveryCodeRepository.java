package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.MfaRecoveryCode;
import com.catcheck.identity.domain.port.MfaRecoveryCodeRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * {@link MfaRecoveryCodeRepository} tren {@code JdbcTemplate}.
 *
 * <p>{@code code_hash} la HMAC-SHA256 hex — KHONG phai BCrypt (p11 §11.12.3).
 * {@code UNIQUE (code_hash)} chan hai admin vo tinh nhan trung ma.</p>
 */
@Repository
public class JdbcMfaRecoveryCodeRepository implements MfaRecoveryCodeRepository {

    private static final String COLUMNS = """
            id, user_id, code_hash, pepper_version, batch_id, used_at, used_ip, created_at
            """;

    private final JdbcTemplate jdbc;

    public JdbcMfaRecoveryCodeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertBatch(UUID userId, List<MfaRecoveryCode> codes) {
        jdbc.batchUpdate("""
                INSERT INTO user_mfa_recovery_code (
                    id, user_id, code_hash, pepper_version, batch_id, created_at
                ) VALUES (?, ?, ?, ?, ?, ?)
                """, codes, codes.size(), (ps, code) -> {
            ps.setObject(1, code.id());
            ps.setObject(2, userId);
            ps.setString(3, code.codeHash());
            ps.setInt(4, code.pepperVersion());
            ps.setObject(5, code.batchId());
            ps.setObject(6, utc(code.createdAt()));
        });
    }

    @Override
    public List<MfaRecoveryCode> findUnusedByUserId(UUID userId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM user_mfa_recovery_code"
                        + " WHERE user_id = ? AND used_at IS NULL ORDER BY created_at",
                (rs, rowNum) -> map(rs), userId);
    }

    @Override
    public boolean markUsed(UUID codeId, UUID userId, Instant usedAt, String usedIp) {
        int updated = jdbc.update("""
                UPDATE user_mfa_recovery_code
                   SET used_at = ?, used_ip = ?
                 WHERE id = ? AND user_id = ? AND used_at IS NULL
                """, utc(usedAt), usedIp, codeId, userId);
        return updated > 0;
    }

    @Override
    public long countUnusedByUserId(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_mfa_recovery_code WHERE user_id = ? AND used_at IS NULL",
                Long.class, userId);
        return count == null ? 0 : count;
    }

    @Override
    public int deleteByUserId(UUID userId) {
        return jdbc.update("DELETE FROM user_mfa_recovery_code WHERE user_id = ?", userId);
    }

    private MfaRecoveryCode map(ResultSet rs) throws SQLException {
        return new MfaRecoveryCode(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuid(rs, "user_id"),
                RowReaders.text(rs, "code_hash"),
                RowReaders.requiredInt(rs, "pepper_version"),
                RowReaders.uuid(rs, "batch_id"),
                RowReaders.instant(rs, "used_at"),
                RowReaders.textOrNull(rs, "used_ip"),
                RowReaders.requiredInstant(rs, "created_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
