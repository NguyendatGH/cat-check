package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link JobHeartbeatCheckJob} — nhóm E, p12 §12.6.6.
 *
 * <p>Điều cần chứng minh: job đọc {@code job_run} gần nhất, so với <b>mốc lẽ ra đã chạy</b> suy
 * ra từ cron, và đếm đúng số job quá hạn — kể cả job chưa từng chạy lần nào. Mốc thời gian cố
 * định 05:05 UTC = 12:05 ICT, tức ngay sau mốc {@code 0 5 * * * *} của
 * {@code ExpireCreditBatchesJob} theo giờ ICT.</p>
 */
class JobHeartbeatCheckJobTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:06:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeJobRunPort port = new FakeJobRunPort();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private JobHeartbeatCheckJob job(JobProperties properties) {
        JobRunRecorder recorder = new JobRunRecorder(port, new UuidV7(CLOCK), CLOCK);
        JobRunner runner = new JobRunner(recorder, properties, meterRegistry, CLOCK);
        return new JobHeartbeatCheckJob(runner, recorder, properties, meterRegistry);
    }

    private static JobProperties properties(boolean reminderEnabled) {
        return new JobProperties(
                true,
                ZoneId.of("Asia/Ho_Chi_Minh"),
                setting(true, "0 5 * * * *", Duration.ofMinutes(50)),
                setting(reminderEnabled, "0 15 * * * *", Duration.ofMinutes(50)),
                setting(true, "0 */10 * * * *", Duration.ofMinutes(5)),
                // Bon job nhom B (W2-B) tat trong test nay: bai test do dem chinh xac so job
                // qua han, nen moi job them vao danh muc ma khong tat se lam con so lech ma
                // KHONG phat hien duoc loi nao that su.
                setting(false, "0 */5 * * * *", Duration.ofSeconds(270)),
                setting(false, "*/30 * * * * *", Duration.ofSeconds(25)),
                setting(false, "0 */15 * * * *", Duration.ofMinutes(14)),
                setting(false, "0 0 4 * * SUN", Duration.ofMinutes(30)),
                setting(false, "0 15 * * * *", Duration.ofMinutes(10)));
    }

    private static JobProperties.JobSetting setting(boolean enabled, String cron, Duration lock) {
        return new JobProperties.JobSetting(enabled, cron, lock, 500, 5_000, false);
    }

    private JobContext context() {
        return new JobContext(UUID.randomUUID(), JobHeartbeatCheckJob.JOB_NAME, NOW, false);
    }

    @Test
    void countsJobsThatNeverRanAsOverdue() {
        JobHeartbeatCheckJob heartbeat = job(properties(true));

        JobOutcome outcome = heartbeat.check(context());

        // Hai job nghiệp vụ đang bật, chưa job nào có dòng job_run ⇒ cả hai quá hạn.
        // Chính JobHeartbeatCheckJob KHÔNG tự đếm mình (p12 §12.6.6).
        assertEquals(2, heartbeat.overdueCount());
        assertEquals(2, outcome.itemsProcessed());
        assertEquals("overdue=ExpireCreditBatchesJob,CreditExpiringReminderJob", outcome.errorSummary());
    }

    @Test
    void aJobThatRanAtItsLastExpectedSlotIsNotOverdue() {
        // 05:05 UTC = 12:05 ICT: đúng mốc gần nhất của cron "0 5 * * * *".
        port.seedFinishedRun(JobProperties.EXPIRE_CREDIT_BATCHES, Instant.parse("2026-10-03T05:05:00Z"));
        port.seedFinishedRun(JobProperties.CREDIT_EXPIRING_REMINDER, Instant.parse("2026-10-03T04:15:00Z"));

        JobHeartbeatCheckJob heartbeat = job(properties(true));
        heartbeat.check(context());

        assertEquals(0, heartbeat.overdueCount());
    }

    @Test
    void aJobThatMissedItsSlotIsOverdue() {
        // Chạy lần cuối cách đây hơn một chu kỳ ⇒ đã bỏ lỡ mốc gần nhất.
        port.seedFinishedRun(JobProperties.EXPIRE_CREDIT_BATCHES, Instant.parse("2026-10-03T02:05:00Z"));
        port.seedFinishedRun(JobProperties.CREDIT_EXPIRING_REMINDER, Instant.parse("2026-10-03T04:15:00Z"));

        JobHeartbeatCheckJob heartbeat = job(properties(true));
        JobOutcome outcome = heartbeat.check(context());

        assertEquals(1, heartbeat.overdueCount());
        assertEquals("overdue=ExpireCreditBatchesJob", outcome.errorSummary());
    }

    @Test
    void disabledJobsAreNotCounted() {
        port.seedFinishedRun(JobProperties.EXPIRE_CREDIT_BATCHES, Instant.parse("2026-10-03T05:05:00Z"));

        JobHeartbeatCheckJob heartbeat = job(properties(false));
        JobOutcome outcome = heartbeat.check(context());

        assertEquals(0, heartbeat.overdueCount());
        assertEquals(1, outcome.itemsProcessed(), "chi con mot job duoc xet");
    }

    @Test
    void publishesTheOverdueGauge() {
        job(properties(true)).check(context());

        assertNotNull(meterRegistry.find("catcheck.job.overdue_count").gauge(),
                "p12 §12.10 doi metric catcheck.job.overdue_count");
        assertEquals(2.0, meterRegistry.find("catcheck.job.overdue_count").gauge().value());
    }
}
