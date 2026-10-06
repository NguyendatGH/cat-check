package com.catcheck.notification.infrastructure.persistence;

import com.catcheck.notification.domain.EmailOutboxMessage;
import com.catcheck.notification.domain.OutboxStatus;
import com.catcheck.notification.domain.port.EmailOutboxRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng {@code email_outbox} (V13, p4 F5). */
@Repository
class JdbcEmailOutboxRepository implements EmailOutboxRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper jsonMapper;

    JdbcEmailOutboxRepository(JdbcTemplate jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<UUID> insertIfAbsent(EmailOutboxMessage message) {
        List<UUID> inserted = jdbc.query("""
                INSERT INTO email_outbox (to_address, template_code, locale, payload, status,
                                          attempts, next_attempt_at, dedupe_key)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                ON CONFLICT (dedupe_key) DO NOTHING
                RETURNING id
                """,
                (rs, rowNum) -> rs.getObject("id", UUID.class),
                message.toAddress(),
                message.templateCode(),
                message.locale(),
                NotificationRowReaders.toJson(jsonMapper, message.payload()),
                message.status().name(),
                message.attempts(),
                NotificationRowReaders.offset(message.nextAttemptAt()),
                message.dedupeKey());
        return inserted.stream().findFirst();
    }

    /**
     * {@code FOR UPDATE SKIP LOCKED} là lớp bảo vệ thứ hai, độc lập với ShedLock (p12 §12.7):
     * nếu một lần chạy vượt {@code lockAtMostFor} thì instance khác có thể giành khoá và chạy
     * song song — khoá ở tầng dòng mới là thứ đảm bảo không gửi trùng.
     */
    @Override
    public List<EmailOutboxMessage> claimPending(Instant now, int limit) {
        return jdbc.query("""
                SELECT id, to_address, template_code, locale, payload, status, attempts,
                       next_attempt_at, last_error, dedupe_key, sent_at, created_at
                  FROM email_outbox
                 WHERE status = 'PENDING' AND next_attempt_at <= ?
                 ORDER BY next_attempt_at
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
                """, (rs, rowNum) -> read(rs), NotificationRowReaders.offset(now), limit);
    }

    /**
     * Gửi xong thì <b>xoá luôn {@code payload}</b>.
     *
     * <p>p12 §12.4 muốn {@code payload} không bao giờ chứa mã OTP thô ("job đọc mã từ
     * {@code email_otp} lúc render"). Điều đó bất khả thi với mô hình của p11 §11.2.1:
     * {@code email_otp} chỉ lưu {@code code_hash = HMAC-SHA256(pepper, purpose‖email‖code)} —
     * một chiều, không giải ra mã được (xem {@code OtpService}). Mã vì thế buộc phải đi qua
     * {@code payload}. Bù lại, cửa sổ tồn tại của nó bị thu hẹp xuống đúng khoảng từ lúc
     * {@code INSERT} tới lúc gửi thành công (≤ 30 giây ở lịch của p12 §12.6.3) thay vì nằm lại
     * trong bảng mãi mãi.</p>
     */
    @Override
    public void markSent(UUID id, Instant sentAt) {
        jdbc.update("""
                UPDATE email_outbox
                   SET status = 'SENT', sent_at = ?, attempts = attempts + 1, last_error = NULL,
                       payload = '{}'::jsonb
                 WHERE id = ?
                """, NotificationRowReaders.offset(sentAt), id);
    }

    @Override
    public void markRetry(UUID id, int attempts, Instant nextAttemptAt, String lastError) {
        jdbc.update("""
                UPDATE email_outbox
                   SET attempts = ?, next_attempt_at = ?, last_error = ?
                 WHERE id = ?
                """, attempts, NotificationRowReaders.offset(nextAttemptAt), lastError, id);
    }

    @Override
    public void markFailed(UUID id, int attempts, String lastError) {
        jdbc.update("""
                UPDATE email_outbox
                   SET status = 'FAILED', attempts = ?, last_error = ?
                 WHERE id = ?
                """, attempts, lastError, id);
    }

    private EmailOutboxMessage read(ResultSet rs) throws SQLException {
        return new EmailOutboxMessage(
                rs.getObject("id", UUID.class),
                rs.getString("to_address"),
                rs.getString("template_code"),
                rs.getString("locale"),
                NotificationRowReaders.json(jsonMapper, rs.getString("payload")),
                OutboxStatus.valueOf(rs.getString("status")),
                rs.getInt("attempts"),
                NotificationRowReaders.instant(rs, "next_attempt_at"),
                rs.getString("last_error"),
                rs.getString("dedupe_key"),
                NotificationRowReaders.instant(rs, "sent_at"),
                NotificationRowReaders.instant(rs, "created_at"));
    }

    @Override
    public boolean requeueFailed(UUID id, Instant now) {
        return jdbc.update("""
                UPDATE email_outbox
                   SET status = 'PENDING', attempts = 0, next_attempt_at = ?, last_error = NULL
                 WHERE id = ? AND status = 'FAILED'
                """, NotificationRowReaders.offset(now), id) == 1;
    }

    @Override
    public Optional<String> findStatus(UUID id) {
        return jdbc.query("SELECT status FROM email_outbox WHERE id = ?",
                (rs, rowNum) -> rs.getString("status"), id).stream().findFirst();
    }
}
