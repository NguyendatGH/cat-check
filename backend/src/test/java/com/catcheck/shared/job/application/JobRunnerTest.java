package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Khuôn chạy chung của job — điều quan trọng nhất ở đây là <b>một job ném ngoại lệ vẫn để lại
 * một dòng {@code job_run} đã đóng</b>, vì nếu không thì "job chết" là thứ không ai nhìn thấy
 * (p12 §12.6.1 quy tắc 4, §12.8.2).
 */
class JobRunnerTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeJobRunPort port = new FakeJobRunPort();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private JobRunner runner(boolean masterSwitch) {
        JobRunRecorder recorder = new JobRunRecorder(port, new UuidV7(CLOCK), CLOCK);
        return new JobRunner(recorder, properties(masterSwitch), meterRegistry, CLOCK);
    }

    private static JobProperties properties(boolean masterSwitch) {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 5 * * * *", Duration.ofMinutes(50), 500, 5_000, false);
        return new JobProperties(masterSwitch, ZoneId.of("Asia/Ho_Chi_Minh"),
                setting, setting, setting, setting, setting, setting, setting, setting);
    }

    @Test
    void successPathClosesTheRowAndRecordsDuration() {
        JobOutcome outcome = runner(true).runScheduled("ExpireCreditBatchesJob", false,
                context -> JobOutcome.of(10, 0, 0, null));

        assertEquals(JobRunStatus.SUCCESS, outcome.status());
        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.SUCCESS, row.status());
        assertEquals(10, row.itemsProcessed());
        assertEquals(NOW, row.finishedAt());
        assertNotNull(meterRegistry.find("catcheck.job.duration")
                .tag("job_name", "ExpireCreditBatchesJob").timer(),
                "p12 §12.10 doi metric catcheck.job.duration theo job_name");
    }

    @Test
    void exceptionInTheBodyStillClosesTheRowAsFailed() {
        JobOutcome outcome = runner(true).runScheduled("ExpireCreditBatchesJob", false,
                context -> {
                    throw new IllegalStateException("khong ket noi duoc DB");
                });

        assertEquals(JobRunStatus.FAILED, outcome.status());
        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.FAILED, row.status());
        assertEquals(NOW, row.finishedAt(), "dong khong duoc ket o trang thai RUNNING");
        assertTrue(row.errorSummary().startsWith("IllegalStateException"));
        assertTrue(row.errorSummary().contains("khong ket noi duoc DB"));
    }

    @Test
    void masterSwitchOffWritesNoRowAtAll() {
        // Một dòng "đã chạy nhưng không làm gì" sẽ khiến JobHeartbeatCheckJob báo job khoẻ
        // mạnh trong khi nó đang bị tắt chủ ý.
        runner(false).runScheduled("ExpireCreditBatchesJob", false,
                context -> {
                    throw new AssertionError("than job khong duoc chay khi cong tac tong tat");
                });

        assertTrue(port.rows.isEmpty());
    }

    @Test
    void dryRunFlagReachesTheRowAndTheBody() {
        runner(true).runScheduled("ExpireCreditBatchesJob", true, context -> {
            assertTrue(context.dryRun(), "than job phai doc duoc co dry-run tu JobContext");
            return JobOutcome.success(3);
        });

        FakeJobRunPort.Row row = port.only();
        assertTrue(row.dryRun());
        assertEquals(3, row.itemsProcessed());
    }

    @Test
    void manualTriggerIsRecorded() {
        runner(true).run("ExpireCreditBatchesJob", JobTriggerType.MANUAL, false,
                context -> JobOutcome.success(0));

        assertEquals(JobTriggerType.MANUAL, port.only().triggerType());
    }
}
