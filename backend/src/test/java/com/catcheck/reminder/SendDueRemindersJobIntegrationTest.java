package com.catcheck.reminder;

import com.catcheck.reminder.application.SendDueRemindersJob;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "catcheck.jobs.enabled=true",
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
class SendDueRemindersJobIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-trixie");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    SendDueRemindersJob job;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aDueReminderProducesAnInAppNotificationRowAndOneJobRunRow() {
        UUID userId = insertUser();
        UUID catId = insertCat(userId, "Mochi");
        UUID reminderId = insertDueReminder(userId, catId);
        long jobRunsBefore = countJobRuns();

        runOneCycle();

        List<Map<String, Object>> inApp = jdbc.queryForList("""
                SELECT channel, template_code, status, title_snapshot, body_snapshot,
                       ref_type, ref_id, dedupe_key, payload->>'deepLink' AS deep_link
                  FROM notification
                 WHERE user_id = ? AND channel = 'IN_APP'
                """, userId);
        assertThat(inApp).hasSize(1);
        assertThat(inApp.getFirst())
                .containsEntry("template_code", "REMINDER_SCAN_DUE")
                .containsEntry("status", "SENT")
                .containsEntry("title_snapshot", "Đến lịch quét định kỳ")
                .containsEntry("body_snapshot", "Đến lịch quét cho Mochi hôm nay.")
                .containsEntry("ref_type", "REMINDER")
                .containsEntry("ref_id", reminderId)
                .containsEntry("deep_link", "/scan?catId=" + catId);

        assertThat(countJobRuns() - jobRunsBefore)
                .as("dung mot dong job_run cho mot lan chay")
                .isEqualTo(1L);
        Map<String, Object> run = latestJobRun();
        assertThat(run)
                .containsEntry("job_name", "SendDueRemindersJob")
                .containsEntry("status", "SUCCESS")
                .containsEntry("row_count", 1);
        assertThat(run.get("finished_at"))
                .as("H15.87: finish() tung that bai voi error_summary NULL, de dong ket RUNNING")
                .isNotNull();
        assertThat(run.get("duration_ms")).isNotNull();

        assertThat(jdbc.queryForObject("""
                SELECT next_run_at > now() AND last_run_at IS NOT NULL
                  FROM reminder WHERE id = ?
                """, Boolean.class, reminderId))
                .as("p12 §12.5.4: next_run_at phai nhay sang chu ky ke tiep, last_run_at duoc dat")
                .isTrue();
        assertThat(jdbc.queryForObject("""
                SELECT to_char(next_run_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI')
                  FROM reminder WHERE id = ?
                """, String.class, reminderId))
                .isEqualTo("08:00");

        runOneCycle();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE user_id = ? AND channel = 'IN_APP'",
                Long.class, userId))
                .as("lan chay thu hai khong duoc sinh them dong notification")
                .isEqualTo(1L);
        assertThat(countJobRuns() - jobRunsBefore)
                .as("van ghi mot dong job_run moi lan chay")
                .isEqualTo(2L);
        assertThat(latestJobRun())
                .as("vong hai khong con lich den han")
                .containsEntry("row_count", 0)
                .containsEntry("status", "SUCCESS");
    }

    private long countJobRuns() {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM job_run WHERE job_name = 'SendDueRemindersJob'", Long.class);
        return count == null ? 0L : count;
    }

    private Map<String, Object> latestJobRun() {
        return jdbc.queryForMap("""
                SELECT job_name, status, row_count, finished_at, duration_ms, error_summary
                  FROM job_run
                 WHERE job_name = 'SendDueRemindersJob'
                 ORDER BY started_at DESC LIMIT 1
                """);
    }

    @Test
    void legacyRruleRemindersWithoutNextRunAreBackfilled() {
        UUID userId = insertUser();
        UUID supported = insertLegacyRrule(userId, insertCat(userId, "Mochi"), "FREQ=WEEKLY;BYDAY=WE,SU");
        UUID unsupported = insertLegacyRrule(userId, insertCat(userId, "Bong"), "FREQ=MONTHLY;BYMONTHDAY=1");

        runOneCycle();

        assertThat(jdbc.queryForObject("""
                SELECT to_char(next_run_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'ID HH24:MI')
                  FROM reminder WHERE id = ?
                """, String.class, supported))
                .isIn("3 09:00", "7 09:00");
        assertThat(jdbc.queryForObject(
                "SELECT next_run_at FROM reminder WHERE id = ?", java.time.OffsetDateTime.class, unsupported))
                .isNull();
    }

    private UUID insertLegacyRrule(UUID userId, UUID catId, String rrule) {
        return jdbc.queryForObject("""
                INSERT INTO reminder (user_id, cat_id, type, schedule_kind, rrule,
                                      preferred_time_start, timezone, next_run_at, channels,
                                      source, active)
                VALUES (?, ?, 'SCAN_ROUTINE', 'RRULE', ?, '09:00', 'Asia/Ho_Chi_Minh',
                        NULL, '["PUSH"]'::jsonb, 'USER', true)
                RETURNING id
                """, UUID.class, userId, catId, rrule);
    }

    @Test
    void creditExpiryRemindersAreLeftToTheCreditJob() {
        UUID userId = insertUser();
        jdbc.update("""
                INSERT INTO reminder (user_id, cat_id, type, schedule_kind, interval_days,
                                      preferred_time_start, timezone, next_run_at, channels,
                                      source, active)
                VALUES (?, NULL, 'CREDIT_EXPIRY', 'INTERVAL', 1, '09:00', 'Asia/Ho_Chi_Minh',
                        now() - interval '10 minutes', '["PUSH"]'::jsonb, 'USER', true)
                """, userId);

        runOneCycle();

        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE user_id = ?", Long.class, userId))
                .isZero();
    }


    private void runOneCycle() {
        jdbc.update("UPDATE shedlock SET lock_until = localtimestamp - interval '1 hour'"
                + " WHERE name = 'SendDueRemindersJob'");
        job.run();
    }


    private UUID insertUser() {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, full_name, locale, timezone, status, email_verified_at)
                VALUES (?, 'Chu nuoi test', 'vi', 'Asia/Ho_Chi_Minh', 'ACTIVE', now())
                RETURNING id
                """, UUID.class, "w2b-" + UUID.randomUUID() + "@example.test");
    }

    private UUID insertCat(UUID ownerId, String name) {
        return jdbc.queryForObject("""
                INSERT INTO cat (owner_id, name, approx_age_months, public_code, status)
                VALUES (?, ?, 24, ?, 'ACTIVE')
                RETURNING id
                """, UUID.class, ownerId, name, publicCode());
    }

    private static String publicCode() {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        StringBuilder code = new StringBuilder("CC-VN-");
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    private UUID insertDueReminder(UUID userId, UUID catId) {
        return jdbc.queryForObject("""
                INSERT INTO reminder (user_id, cat_id, type, schedule_kind, interval_days,
                                      preferred_time_start, preferred_time_end, timezone,
                                      next_run_at, channels, source, active)
                VALUES (?, ?, 'SCAN_ROUTINE', 'INTERVAL', 3, '08:00', '10:00',
                        'Asia/Ho_Chi_Minh', now() - interval '10 minutes',
                        '["PUSH", "IN_APP"]'::jsonb, 'USER', true)
                RETURNING id
                """, UUID.class, userId, catId);
    }
}
