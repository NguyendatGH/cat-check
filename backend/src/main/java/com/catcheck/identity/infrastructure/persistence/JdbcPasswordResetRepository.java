package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.port.PasswordResetRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * {@link PasswordResetRepository} tren {@code JdbcTemplate}.
 *
 * <p>Luong hien tai theo p11 §11.2.6 dung OTP + {@code otp_ticket}, khong dung magic
 * link. Bang nay giu lai bang chung {@code PASSWORD_RESET}: {@code token_hash} =
 * SHA-256 cua ticket, {@code consumed_at} = luc doi mat khau. Xem {@code V5__identity.sql}
 * va {@code docs/handovers/A1.md}.</p>
 */
@Repository
public class JdbcPasswordResetRepository implements PasswordResetRepository {

    /** Khong ghi `created_at`: de DB gan `DEFAULT now()` de thoi diem mang theo server. */
    private static final String INSERT = """
            INSERT INTO password_reset (id, user_id, token_hash, expires_at, request_ip)
            VALUES (?, ?, ?, ?, ?::inet)
            """;

    private final JdbcTemplate jdbc;

    public JdbcPasswordResetRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(UUID userId, String tokenHash, Instant expiresAt, String requestIp) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, UUID.randomUUID());
            ps.setObject(i++, userId);
            ps.setString(i++, tokenHash);
            ps.setObject(i++, utc(expiresAt));
            if (requestIp == null) {
                ps.setNull(i++, Types.VARCHAR);
            } else {
                ps.setString(i, requestIp);
            }
            return ps;
        });
    }

    @Override
    public void markConsumed(String tokenHash, Instant consumedAt) {
        jdbc.update("UPDATE password_reset SET consumed_at = ? WHERE token_hash = ?",
                utc(consumedAt), tokenHash);
    }

    @Override
    public boolean isConsumed(String tokenHash) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset WHERE token_hash = ? AND consumed_at IS NOT NULL",
                Integer.class, tokenHash);
        return count != null && count > 0;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
