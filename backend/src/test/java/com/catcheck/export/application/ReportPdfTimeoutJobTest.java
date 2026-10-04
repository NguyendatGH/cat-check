package com.catcheck.export.application;

import com.catcheck.export.domain.ExportJob;
import com.catcheck.export.domain.ExportStatus;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobRunStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ReportPdfTimeoutJob} — lưới an toàn cho dòng {@code export_job} kẹt
 * {@code QUEUED}/{@code RUNNING} (H15.76 / H15.78).
 *
 * <p>Bài test quan trọng nhất là bài cuối: sau khi job dọn, user <b>xuất lại được</b>. Đó mới là
 * hậu quả thật của bất biến I25, không phải một cột trạng thái đổi giá trị.</p>
 */
class ReportPdfTimeoutJobTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID CAT_ID = UUID.fromString("00000000-0000-0000-0000-0000000000c1");
    private static final Instant NOW = Instant.parse("2026-10-03T04:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeExportJobRepository repository = new FakeExportJobRepository();
    private final ExportTimeoutSweepService sweepService = new ExportTimeoutSweepService(repository);

    @Test
    @DisplayName("Job QUEUED quá 15 phút (p13 §13.6.1) chuyển sang FAILED kèm lý do đọc được")
    void closesJobsStuckLongerThanTheTimeout() {
        UUID stuck = seed(NOW.minus(Duration.ofMinutes(20)), ExportStatus.QUEUED);

        JobOutcome outcome = job().failStaleJobs(context(false));

        assertThat(outcome.status()).isEqualTo(JobRunStatus.SUCCESS);
        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        ExportJob closed = repository.findById(stuck).orElseThrow();
        assertThat(closed.getStatus()).isEqualTo(ExportStatus.FAILED);
        assertThat(closed.getFailureReason()).isEqualTo(ExportTimeoutSweepService.TIMEOUT_REASON);
        assertThat(closed.getCompletedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("Job mới submit chưa quá hạn thì KHÔNG bị đánh hỏng oan")
    void leavesFreshJobsAlone() {
        UUID fresh = seed(NOW.minus(Duration.ofMinutes(2)), ExportStatus.RUNNING);

        JobOutcome outcome = job().failStaleJobs(context(false));

        assertThat(outcome.itemsProcessed()).isZero();
        assertThat(repository.findById(fresh).orElseThrow().getStatus()).isEqualTo(ExportStatus.RUNNING);
    }

    @Test
    @DisplayName("dry_run chỉ đếm, không đổi trạng thái dòng nào (p15 REQ-RET-01)")
    void dryRunOnlyCounts() {
        UUID stuck = seed(NOW.minus(Duration.ofHours(3)), ExportStatus.QUEUED);

        JobOutcome outcome = job().failStaleJobs(context(true));

        assertThat(outcome.itemsProcessed()).isEqualTo(1);
        assertThat(outcome.errorSummary()).contains("dry-run");
        assertThat(repository.findById(stuck).orElseThrow().getStatus()).isEqualTo(ExportStatus.QUEUED);
    }

    @Test
    @DisplayName("Chạy lại là vô hại — dòng đã FAILED không bị xử lý lần hai")
    void runningTwiceIsIdempotent() {
        seed(NOW.minus(Duration.ofMinutes(30)), ExportStatus.QUEUED);
        job().failStaleJobs(context(false));

        JobOutcome second = job().failStaleJobs(context(false));

        assertThat(second.itemsProcessed()).isZero();
    }

    @Test
    @DisplayName("Sau khi dọn, user tạo được export mới — I25 không còn chặn")
    void theUserCanExportAgainAfterTheSweep() {
        seed(NOW.minus(Duration.ofHours(1)), ExportStatus.QUEUED);
        assertThat(repository.findActiveByUser(USER_ID)).isPresent();

        job().failStaleJobs(context(false));

        assertThat(repository.findActiveByUser(USER_ID))
                .as("khong con job QUEUED/RUNNING nao ⇒ POST /exports khong con tra 409")
                .isEmpty();
        // Chứng minh bằng chính ràng buộc I25 của fake repository: ghi dòng mới không còn vỡ.
        repository.save(newJob(NOW, ExportStatus.QUEUED));
        assertThat(repository.findActiveByUser(USER_ID)).isPresent();
    }

    // ------------------------------------------------------------------ dàn dựng

    /**
     * {@link JobRunner} để {@code null} có chủ đích: test gọi thẳng thân job
     * ({@code failStaleJobs}) nên không chạm tới runner, và dựng runner thật ở đây sẽ buộc test
     * này phải sửa mỗi lần {@code JobProperties} thêm một job mới. Khuôn mở/đóng dòng
     * {@code job_run} đã có test riêng ở {@code JobRunnerTest}.
     */
    private ReportPdfTimeoutJob job() {
        return new ReportPdfTimeoutJob(null, sweepService, ReportPdfTimeoutJob.DEFAULT_STALE_AFTER, false);
    }

    private JobContext context(boolean dryRun) {
        return new JobContext(UUID.randomUUID(), ReportPdfTimeoutJob.JOB_NAME, NOW, dryRun);
    }

    private UUID seed(Instant requestedAt, ExportStatus status) {
        ExportJob job = newJob(requestedAt, status);
        repository.save(job);
        return job.getId();
    }

    private ExportJob newJob(Instant requestedAt, ExportStatus status) {
        ExportJob job = new ExportJob(UUID.randomUUID(), USER_ID, CAT_ID,
                "CC-EXP-2026-" + (100000 + (int) (Math.abs(requestedAt.getEpochSecond()) % 900000)),
                LocalDate.of(2026, 9, 4), LocalDate.of(2026, 10, 3), "30D",
                List.of("TREND"), "vi", "Asia/Ho_Chi_Minh", requestedAt);
        if (status == ExportStatus.RUNNING) {
            job.markRunning();
        }
        return job;
    }
}
