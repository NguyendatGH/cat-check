package com.catcheck.shared.job.application;

import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunStatus;
import com.catcheck.shared.job.JobTriggerType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JobRunRecorder} với cổng trong bộ nhớ — kiểm chứng hợp đồng "đúng một dòng
 * {@code job_run} mỗi lần chạy" (p12 §12.6.1 quy tắc 4) mà không cần DB.
 */
class JobRunRecorderTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeJobRunPort port = new FakeJobRunPort();
    private final JobRunRecorder recorder = new JobRunRecorder(port, new UuidV7(CLOCK), CLOCK);

    @Test
    void startOpensExactlyOneRunningRow() {
        JobContext context = recorder.start("ExpireCreditBatchesJob", JobTriggerType.SCHEDULE, false);

        FakeJobRunPort.Row row = port.only();
        assertEquals("ExpireCreditBatchesJob", row.jobName());
        assertEquals(JobRunStatus.RUNNING, row.status());
        assertEquals(JobTriggerType.SCHEDULE, row.triggerType());
        assertEquals(NOW, row.startedAt());
        assertNull(row.finishedAt(), "dong vua mo phai con finished_at NULL (p4 §K3)");
        assertEquals(context.runId(), row.runId());
        assertEquals(NOW, context.startedAt());
        assertFalse(context.dryRun());
    }

    @Test
    void dryRunStillWritesARow() {
        // p4 §K3: "Lần chạy dry_run VẪN ghi một dòng — nếu không, người vận hành không biết
        // mình đã thử gì".
        recorder.start("ScanImageRetentionJob", JobTriggerType.MANUAL, true);

        FakeJobRunPort.Row row = port.only();
        assertTrue(row.dryRun());
        assertEquals(JobTriggerType.MANUAL, row.triggerType());
    }

    @Test
    void finishClosesTheRowWithStatusAndCounts() {
        JobContext context = recorder.start("ExpireCreditBatchesJob", JobTriggerType.SCHEDULE, false);

        recorder.finish(context.runId(), JobOutcome.of(42, 7, 2, "2 lo loi"));

        FakeJobRunPort.Row row = port.only();
        assertEquals(JobRunStatus.PARTIAL, row.status(), "co item loi thi trang thai la PARTIAL");
        assertEquals(42, row.itemsProcessed());
        assertEquals(7, row.itemsDeleted());
        assertEquals(2, row.itemsFailed());
        assertEquals("2 lo loi", row.errorSummary());
        assertEquals(NOW, row.finishedAt());
    }

    @Test
    void finishOnUnknownRunIdFailsLoudly() {
        // Đóng một dòng không tồn tại nghĩa là có chỗ gọi finish mà chưa gọi start — im lặng
        // ở đây sẽ sinh ra job chạy mà không có nhật ký.
        assertThrows(IllegalStateException.class,
                () -> recorder.finish(java.util.UUID.randomUUID(), JobOutcome.success(0)));
    }

    @Test
    void lastStartedAtReadsBackTheMostRecentRun() {
        port.seedFinishedRun("ExpireCreditBatchesJob", NOW.minusSeconds(7_200));
        port.seedFinishedRun("ExpireCreditBatchesJob", NOW.minusSeconds(3_600));

        assertEquals(Optional.of(NOW.minusSeconds(3_600)),
                recorder.lastStartedAt("ExpireCreditBatchesJob"));
        assertTrue(recorder.lastStartedAt("CreditExpiringReminderJob").isEmpty());
    }

    @Test
    void outcomeRejectsRunningAndNegativeCounts() {
        // JobOutcome là nơi duy nhất chặn giá trị vô nghĩa trước khi chạm ck_job_run_count.
        assertThrows(IllegalArgumentException.class,
                () -> new JobOutcome(JobRunStatus.RUNNING, 0, 0, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> new JobOutcome(JobRunStatus.SUCCESS, -1, 0, 0, null));
    }

    @Test
    void outcomeTruncatesErrorSummary() {
        // p4 §K3: error_summary phải "rút gọn, không stack trace".
        String overlyLong = "x".repeat(JobOutcome.MAX_ERROR_SUMMARY_LENGTH + 100);
        JobOutcome outcome = JobOutcome.failed(overlyLong);
        assertEquals(JobOutcome.MAX_ERROR_SUMMARY_LENGTH, outcome.errorSummary().length());
    }
}
