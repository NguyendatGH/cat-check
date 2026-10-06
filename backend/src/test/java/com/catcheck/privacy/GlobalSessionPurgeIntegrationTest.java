package com.catcheck.privacy;

import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import com.catcheck.shared.i18n.I18nProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * L62 / runbook R1 (p11 §11.13.4) chạy <b>thật</b> trên PostgreSQL thật (Testcontainers +
 * Flyway đầy đủ).
 *
 * <p><b>Vì sao bài này bắt buộc phải là integration test, không phải unit test:</b> thứ L62
 * hứa là "mọi phiên biến mất", và lời hứa đó nằm trọn trong SQL + schema, không nằm trong
 * logic Java. Bốn thứ chỉ PostgreSQL thật mới kiểm được:</p>
 * <ol>
 *   <li>{@code SPRING_SESSION_ATTRIBUTES} có {@code ON DELETE CASCADE} tới
 *       {@code SPRING_SESSION} (V3) — nếu không, một câu {@code DELETE} vào bảng cha sẽ ném
 *       lỗi khoá ngoại ngay giữa lúc đang xử lý sự cố.</li>
 *   <li>{@code ck_device_session_revoked CHECK ((revoked_at IS NULL) = (revoke_reason IS NULL))}
 *       và {@code ck_device_session_reason} — đặt sai một trong hai là câu UPDATE bị DB chặn.</li>
 *   <li>{@code ck_email_otp_verified_pair CHECK ((verified_at IS NULL) = (ticket_hash IS NULL))}
 *       — đây đúng là lý do adapter <b>không</b> NULL {@code ticket_hash} mà hết hạn
 *       {@code ticket_expires_at}.</li>
 *   <li>Câu lệnh phải là thao tác theo tập hợp để đạt mục tiêu ≤ 5 phút cho toàn hệ thống.</li>
 * </ol>
 *
 * <p><b>Và vì sao KHÔNG bấm L62 thật trên DB dùng chung của máy dev:</b> nó xoá
 * {@code spring_session} của <i>mọi</i> người — mọi phiên admin của mọi agent đang làm việc
 * trên cùng DB {@code catcheck} sẽ bị đá ra ngay. Container của bài test này có DB riêng, dữ
 * liệu riêng, nên chứng minh được cùng điều đó mà không phá việc của ai.</p>
 */
@SpringBootTest(properties = {
        "catcheck.jobs.enabled=false",
        "catcheck.jobs.send-due-reminders.cron=0 0 4 1 1 *",
        "catcheck.jobs.send-email-outbox.cron=0 0 4 1 1 *",
        "catcheck.jobs.retry-failed-notifications.cron=0 0 4 1 1 *",
        "catcheck.jobs.cleanup-dead-push-tokens.cron=0 0 4 1 1 *",
        "catcheck.jobs.expire-credit-batches.cron=0 0 4 1 1 *",
        "catcheck.jobs.credit-expiring-reminder.cron=0 0 4 1 1 *",
        "catcheck.jobs.job-heartbeat-check.cron=0 0 4 1 1 *"
})
@EnableConfigurationProperties(I18nProperties.class)
@ActiveProfiles("test")
@Testcontainers
class GlobalSessionPurgeIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    GlobalSessionPurgePort purgePort;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void purgingRemovesEverySpringSessionRowAndItsAttributesAndClosesEveryOpenOtp() {
        UUID userId = insertUser();
        String primaryId = insertSpringSession(userId);
        insertSpringSessionAttribute(primaryId);
        UUID deviceSessionId = insertDeviceSession(userId);
        UUID openOtpId = insertOpenOtp(userId, "LOGIN_STEPUP");
        UUID ticketOtpId = insertVerifiedTicketOtp(userId);

        GlobalSessionPurgePort.PurgeOutcome outcome = purgePort.purgeEverySession();

        /* ---- 1. phiên thật biến mất, và bảng attribute biến mất theo (CASCADE của V3) ---- */
        assertThat(outcome.httpSessionsDeleted()).isGreaterThanOrEqualTo(1);
        assertThat(count("SELECT count(*) FROM spring_session")).isZero();
        assertThat(count("SELECT count(*) FROM spring_session_attributes")).isZero();

        /* ---- 2. bản sao hiển thị được đánh dấu, đúng cặp CHECK revoked_at/revoke_reason ---- */
        assertThat(outcome.deviceSessionsRevoked()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT revoke_reason FROM user_device_session WHERE id = ?", String.class, deviceSessionId))
                .isEqualTo("ADMIN_LOCK");
        assertThat(jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM user_device_session WHERE id = ?",
                Boolean.class, deviceSessionId)).isTrue();

        /* ---- 3. OTP đang mở bị đóng — một mã còn sống là một đường đăng nhập (§11.13.4) ---- */
        assertThat(outcome.otpInvalidated()).isGreaterThanOrEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT consumed_at IS NOT NULL FROM email_otp WHERE id = ?", Boolean.class, openOtpId))
                .isTrue();

        /* ---- 4. ticket chưa dùng hết hạn, nhưng ticket_hash/verified_at còn nguyên cặp ---- */
        assertThat(jdbc.queryForObject(
                "SELECT ticket_expires_at <= now() FROM email_otp WHERE id = ?", Boolean.class, ticketOtpId))
                .as("ticket đặt lại mật khẩu không còn dùng được")
                .isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT (verified_at IS NULL) = (ticket_hash IS NULL) FROM email_otp WHERE id = ?",
                Boolean.class, ticketOtpId))
                .as("ck_email_otp_verified_pair vẫn thoả — đây là lý do không NULL ticket_hash")
                .isTrue();
    }

    @Test
    void purgingAnAlreadyEmptySystemIsIdempotentAndReturnsZeroes() {
        purgePort.purgeEverySession();

        GlobalSessionPurgePort.PurgeOutcome second = purgePort.purgeEverySession();

        assertThat(second.httpSessionsDeleted()).isZero();
        assertThat(second.deviceSessionsRevoked()).isZero();
        assertThat(second.otpInvalidated()).isZero();
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private UUID insertUser() {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Chu nuoi test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "w5c-" + UUID.randomUUID() + "@example.test");
    }

    private String insertSpringSession(UUID userId) {
        String primaryId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time,
                                            max_inactive_interval, expiry_time, principal_name)
                VALUES (?, ?, ?, ?, 28800, ?, ?)
                """, primaryId, UUID.randomUUID().toString(),
                System.currentTimeMillis(), System.currentTimeMillis(),
                System.currentTimeMillis() + 28_800_000, userId.toString());
        return primaryId;
    }

    private void insertSpringSessionAttribute(String primaryId) {
        jdbc.update("""
                INSERT INTO spring_session_attributes (session_primary_id, attribute_name, attribute_bytes)
                VALUES (?, 'SPRING_SECURITY_CONTEXT', ?)
                """, primaryId, new byte[] {1, 2, 3});
    }

    private UUID insertDeviceSession(UUID userId) {
        return jdbc.queryForObject("""
                INSERT INTO user_device_session (id, user_id, session_id_hash, device_label,
                                                 remember_me, expires_at)
                VALUES (gen_random_uuid(), ?, ?, 'Trinh duyet tren Linux', true, now() + interval '8 hours')
                RETURNING id
                """, UUID.class, userId, "a".repeat(64));
    }

    private UUID insertOpenOtp(UUID userId, String purpose) {
        return jdbc.queryForObject("""
                INSERT INTO email_otp (user_id, email, code_hash, purpose, expires_at)
                VALUES (?, ?, ?, ?, now() + interval '5 minutes')
                RETURNING id
                """, UUID.class, userId, "open-" + UUID.randomUUID() + "@example.test",
                "b".repeat(64), purpose);
    }

    private UUID insertVerifiedTicketOtp(UUID userId) {
        return jdbc.queryForObject("""
                INSERT INTO email_otp (user_id, email, code_hash, purpose, expires_at,
                                       verified_at, ticket_hash, ticket_expires_at)
                VALUES (?, ?, ?, 'PASSWORD_RESET', now() + interval '5 minutes',
                        now(), ?, now() + interval '15 minutes')
                RETURNING id
                """, UUID.class, userId, "ticket-" + UUID.randomUUID() + "@example.test",
                "c".repeat(64), "d".repeat(64));
    }
}
