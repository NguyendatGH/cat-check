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

/**
 * {@code SendDueRemindersJob} chạy <b>thật</b>: PostgreSQL thật (Testcontainers + Flyway đầy
 * đủ), {@code NotificationService} thật, repository JDBC thật, {@code JobRunner} thật ghi vào
 * {@code job_run} thật.
 *
 * <p><b>Vì sao cần bài này bên cạnh {@code SendDueRemindersJobTest}:</b> bài kia dùng fake nên
 * nó chứng minh <i>logic</i> đúng, không chứng minh <i>SQL</i> đúng. Bốn thứ chỉ có PostgreSQL
 * thật mới bắt được, và cả bốn đều nằm trong code mới của gói việc này:</p>
 * <ol>
 *   <li>{@code SELECT … FOR UPDATE SKIP LOCKED} sai cú pháp hoặc sai cột sẽ ném lúc chạy, không
 *       lúc biên dịch.</li>
 *   <li>{@code notification.dedupe_key} là UNIQUE trên TOÀN bảng — va khoá là hành vi của DB.</li>
 *   <li>{@code channels}/{@code payload} là JSONB, phải ép {@code ?::jsonb}.</li>
 *   <li>{@code job_run} ở V15 hẹp hơn p12 §12.8.2 (H15.44) nên adapter phải xuống thang —
 *       chỉ chạy thật mới biết nó còn ghi được.</li>
 * </ol>
 *
 * <p>Lịch cron của mọi job bị đẩy sang "01/01 04:00" để scheduler không tự chạy giữa bài test;
 * job được gọi tay qua {@code run()}. {@code catcheck.jobs.enabled} vẫn {@code true} vì đó là
 * công tắc quyết định có ghi {@code job_run} hay không — thứ bài test này đang kiểm.</p>
 */
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
        // Hai bai test dung chung mot context + mot DB, va JUnit khong bao dam thu tu, nen moi
        // khang dinh ve job_run phai do DELTA chu khong do tong so dong.
        long jobRunsBefore = countJobRuns();

        runOneCycle();

        /* ---- 1. có dòng `notification` IN_APP mới, đúng template + nội dung p12 §12.2.2 ---- */
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

        /* ---- 2. `job_run` ghi đúng MỘT dòng, đã đóng (p12 §12.6.1 quy tắc 4) ---- */
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

        /* ---- 3. `next_run_at` nhảy sang tương lai, `last_run_at` được đặt ---- */
        // So sánh ngay trong SQL: `queryForMap` trả timestamptz về dưới dạng
        // `java.sql.Timestamp`, mà ArchUnit R13 cấm type đó xuất hiện trong repo.
        assertThat(jdbc.queryForObject("""
                SELECT next_run_at > now() AND last_run_at IS NOT NULL
                  FROM reminder WHERE id = ?
                """, Boolean.class, reminderId))
                .as("p12 §12.5.4: next_run_at phai nhay sang chu ky ke tiep, last_run_at duoc dat")
                .isTrue();
        // Đúng mốc p12 §12.5.4: 3 ngày sau, tại preferred_time_start 08:00 giờ ICT.
        assertThat(jdbc.queryForObject("""
                SELECT to_char(next_run_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI')
                  FROM reminder WHERE id = ?
                """, String.class, reminderId))
                .isEqualTo("08:00");

        /* ---- 4. chạy lại KHÔNG gửi trùng (p12 §12.5.5) ---- */
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
    void creditExpiryRemindersAreLeftToTheCreditJob() {
        // p12 §12.5.1: mốc T-48h/T-6h tính từ credit_batch.expires_at, không từ lịch người dùng.
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


    /**
     * Một chu kỳ scheduler.
     *
     * <p><b>Phải nhả khoá ShedLock trước mỗi lần gọi.</b> {@code @SchedulerLock} được áp qua
     * proxy AOP nên nó chạy cả khi {@code run()} được gọi tay, và
     * {@code lockAtLeastFor = PT10S} giữ khoá thêm 10 giây <i>sau khi</i> job xong — cố ý, để
     * đồng hồ lệch giữa các instance không làm hai instance chạy liền nhau. Hệ quả trong test:
     * hai lần gọi cách nhau vài milli-giây thì lần thứ hai bị <b>bỏ qua lặng lẽ</b> (không log,
     * không dòng {@code job_run}) — đúng hành vi production, nhưng không phải thứ bài test này
     * muốn đo. Đẩy {@code lock_until} về quá khứ = mô phỏng hai chu kỳ cách nhau 5 phút thật.</p>
     *
     * <p><b>{@code UPDATE} chứ không {@code DELETE}:</b> {@code JdbcTemplateLockProvider} nhớ
     * trong bộ nhớ những tên khoá nó đã {@code INSERT} để lần sau khỏi thử insert lại. Xoá
     * dòng đi thì nó chỉ chạy {@code UPDATE}, thấy 0 dòng bị ảnh hưởng và kết luận "người khác
     * đang giữ khoá" — job bị bỏ qua lặng lẽ, không log, không dòng {@code job_run}. Đã trả
     * giá bằng một lần test đỏ khó hiểu để biết điều này.</p>
     */
    private void runOneCycle() {
        jdbc.update("UPDATE shedlock SET lock_until = localtimestamp - interval '1 hour'"
                + " WHERE name = 'SendDueRemindersJob'");
        job.run();
    }

    /* ------------------------------------------------------------------------ dữ liệu thật */

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

    /**
     * {@code ck_cat_public_code} doi dung {@code CC-VN-<6 ky tu Crockford Base32>} (p4 C1) —
     * bo I/L/O/U. Sinh tai day thay vi hard-code de hai lan chay test khong dung
     * {@code uq_cat_public_code}.
     */
    private static String publicCode() {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        StringBuilder code = new StringBuilder("CC-VN-");
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /** Tới hạn 10 phút trước — đúng tình huống job được sinh ra để xử lý. */
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
