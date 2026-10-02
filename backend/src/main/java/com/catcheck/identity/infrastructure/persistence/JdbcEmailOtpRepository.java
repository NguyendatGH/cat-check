package com.catcheck.identity.infrastructure.persistence;

import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.port.EmailOtpRepository;
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
 * {@link EmailOtpRepository} tren {@code JdbcTemplate}.
 *
 * <p>Co mot bat bien o cap DB can giu nguyen: {@code uq_email_otp_active_per_purpose}
 * la unique index cuc bo tren {@code (email, purpose) WHERE consumed_at IS NULL}.
 * No chan hai request gui ma chay song song — ca hai deu "khong co challenge ACTIVE"
 * roi deu thay — nen <b>moi thao tac {@code insert} phai nam trong mot transaction</b>
 * co {@code supersedeActive} chay truoc. Hai lan insert deu duoc, se co mot
 * {@code DuplicateKeyException} la thu hai, va tang {@code attempt_count} de chan
 * thu doan. {@code OtpService} bat buoc co {@code @Transactional} vi chuyen.</p>
 */
@Repository
public class JdbcEmailOtpRepository implements EmailOtpRepository {

    private static final String COLUMNS = """
            id, user_id, email, code_hash, pepper_version, purpose, expires_at,
            attempt_count, max_attempts, consumed_at, request_ip,
            verified_at, ticket_hash, ticket_expires_at, created_at
            """;

    private static final String INSERT = """
            INSERT INTO email_otp (
                id, user_id, email, code_hash, pepper_version, purpose, expires_at,
                max_attempts, request_ip, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcEmailOtpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<EmailOtpChallenge> findActive(String email, OtpPurpose purpose) {
        return one("SELECT " + COLUMNS + " FROM email_otp"
                + " WHERE email = ? AND purpose = ? AND consumed_at IS NULL"
                + " ORDER BY created_at DESC LIMIT 1", email, purpose.name());
    }

    @Override
    public Optional<EmailOtpChallenge> findById(UUID challengeId) {
        return one("SELECT " + COLUMNS + " FROM email_otp WHERE id = ?", challengeId);
    }

    @Override
    public Optional<EmailOtpChallenge> findByTicketHash(String ticketHash) {
        return one("SELECT " + COLUMNS + " FROM email_otp WHERE ticket_hash = ?", ticketHash);
    }

    @Override
    public void insert(EmailOtpChallenge challenge) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            int i = 1;
            ps.setObject(i++, challenge.id());
            if (challenge.userId() == null) {
                ps.setNull(i++, Types.OTHER);
            } else {
                ps.setObject(i++, challenge.userId());
            }
            ps.setString(i++, challenge.email());
            ps.setString(i++, challenge.codeHash());
            ps.setInt(i++, challenge.pepperVersion());
            ps.setString(i++, challenge.purpose().name());
            ps.setObject(i++, utc(challenge.expiresAt()));
            ps.setInt(i++, challenge.maxAttempts());
            if (challenge.requestIp() == null) {
                ps.setNull(i++, Types.VARCHAR);
            } else {
                ps.setString(i++, challenge.requestIp());
            }
            ps.setObject(i, utc(challenge.createdAt()));
            return ps;
        });
    }

    @Override
    public void supersedeActive(String email, OtpPurpose purpose, Instant consumedAt) {
        jdbc.update("""
                UPDATE email_otp SET consumed_at = ?
                 WHERE email = ? AND purpose = ? AND consumed_at IS NULL
                """, utc(consumedAt), email, purpose.name());
    }

    @Override
    public int incrementAttempt(UUID challengeId) {
        Integer after = jdbc.queryForObject(
                "UPDATE email_otp SET attempt_count = attempt_count + 1 WHERE id = ? RETURNING attempt_count",
                Integer.class, challengeId);
        return after == null ? 0 : after;
    }

    @Override
    public void markVerified(UUID challengeId, Instant verifiedAt, String ticketHash, Instant ticketExpiresAt) {
        jdbc.update("""
                UPDATE email_otp
                   SET verified_at = ?, ticket_hash = ?, ticket_expires_at = ?
                 WHERE id = ? AND consumed_at IS NULL
                """, utc(verifiedAt), ticketHash, utc(ticketExpiresAt), challengeId);
    }

    @Override
    public void markConsumed(UUID challengeId, Instant consumedAt) {
        jdbc.update("UPDATE email_otp SET consumed_at = ? WHERE id = ?", utc(consumedAt), challengeId);
    }

    @Override
    public int deleteOlderThan(Instant createdBefore) {
        // p11 §11.2.1: giu 24 gio phuc vu dieu tra brute-force roi job xoa.
        return jdbc.update("DELETE FROM email_otp WHERE created_at < ?", utc(createdBefore));
    }

    private Optional<EmailOtpChallenge> one(String sql, Object... args) {
        List<EmailOtpChallenge> rows = jdbc.query(sql, (rs, rowNum) -> map(rs), args);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private EmailOtpChallenge map(ResultSet rs) throws SQLException {
        return new EmailOtpChallenge(
                RowReaders.uuid(rs, "id"),
                RowReaders.uuidOrNull(rs, "user_id"),
                RowReaders.text(rs, "email"),
                RowReaders.text(rs, "code_hash"),
                RowReaders.requiredInt(rs, "pepper_version"),
                RowReaders.requiredEnum(rs, "purpose", OtpPurpose.class),
                RowReaders.requiredInstant(rs, "expires_at"),
                RowReaders.requiredInt(rs, "attempt_count"),
                RowReaders.requiredInt(rs, "max_attempts"),
                RowReaders.instant(rs, "consumed_at"),
                RowReaders.textOrNull(rs, "request_ip"),
                RowReaders.instant(rs, "verified_at"),
                RowReaders.textOrNull(rs, "ticket_hash"),
                RowReaders.instant(rs, "ticket_expires_at"),
                RowReaders.requiredInstant(rs, "created_at"));
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
