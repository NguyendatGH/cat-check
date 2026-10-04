package com.catcheck.export.application;

import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * <b>Lưới an toàn cho {@code ReportPdfJob}</b> — đặt mọi {@code export_job} còn
 * {@code QUEUED}/{@code RUNNING} quá <b>15 phút</b> thành {@code FAILED} kèm lý do đọc được.
 *
 * <p><b>Vì sao bắt buộc phải có.</b> p4 G1 (bất biến I25) có
 * {@code UNIQUE(user_id) WHERE status IN ('QUEUED','RUNNING')}: đúng một dòng kẹt là user đó
 * <b>vĩnh viễn</b> nhận {@code 409 EXPORT_JOB_IN_PROGRESS} cho mọi lần xuất sau. Mà dòng kẹt
 * không chặn hết được bằng code trong tiến trình: app chết giữa lúc render, bể luồng bị bỏ lúc
 * shutdown, hay máy mất điện đều để lại đúng tình trạng đó. {@link ExportJobDispatcher} chỉ sửa
 * nguyên nhân thường gặp nhất (H15.76 — submit executor trước COMMIT); job này dọn phần còn
 * lại.</p>
 *
 * <p><b>Ngưỡng 15 phút</b> lấy từ p13 §13.6.1 bảng so sánh: {@code ReportPdfJob} có
 * "Timeout / retry: 15m mỗi instance". Quá mốc đó thì theo spec job đã hỏng, không phải đang
 * chạy chậm.</p>
 *
 * <p><b>Cảnh báo về tên job và lịch chạy (H15.78).</b> p12 §12.6 là danh mục job DUY NHẤT của
 * dự án và nó <b>không có dòng nào</b> cho việc dọn job xuất PDF kẹt — {@code ReportPdfJob} ở
 * §12.6.3 là bản thân việc sinh PDF (theo sự kiện, không cron). Tên
 * {@code ReportPdfTimeoutJob}, cron mỗi 15 phút và {@code lockAtMostFor = 10m} ở đây là
 * <b>quy ước của repo</b>, đặt cạnh cụm "liên tục · 15m" đã có trong bản đồ giờ chạy §12.6.8
 * ({@code PurgeStagedScanFilesJob}, {@code RetryFailedNotificationsJob},
 * {@code IncidentDeadlineMonitorJob}). Cần p12 chốt lại.</p>
 *
 * <p><b>Cố ý KHÔNG khai trong {@code JobProperties}</b> (H15.79): thêm một thành phần vào record
 * đó buộc phải sửa 4 file test nằm ngoài vùng file của gói việc này. Hệ quả phải biết:
 * {@code JobHeartbeatCheckJob} đọc {@code JobProperties.scheduledJobs()} nên <b>chưa giám sát
 * job này</b> — nó vẫn ghi {@code job_run} đầy đủ qua {@link JobRunner}, chỉ là không có ai báo
 * khi nó chết âm thầm.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.report-pdf-timeout", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ReportPdfTimeoutJob {

    /** {@code job_run.job_name} — xem cảnh báo về tên ở javadoc lớp. */
    public static final String JOB_NAME = "ReportPdfTimeoutJob";

    /** p13 §13.6.1: {@code ReportPdfJob} timeout 15 phút mỗi instance. */
    static final Duration DEFAULT_STALE_AFTER = Duration.ofMinutes(15);

    private static final int BATCH_SIZE = 200;
    private static final int MAX_ITEMS_PER_RUN = 1_000;

    private static final Logger log = LoggerFactory.getLogger(ReportPdfTimeoutJob.class);

    private final JobRunner jobRunner;
    private final ExportTimeoutSweepService sweepService;
    private final Duration staleAfter;
    private final boolean dryRun;

    public ReportPdfTimeoutJob(
            JobRunner jobRunner,
            ExportTimeoutSweepService sweepService,
            @Value("${catcheck.jobs.report-pdf-timeout.stale-after:PT15M}") Duration staleAfter,
            @Value("${catcheck.jobs.report-pdf-timeout.dry-run:false}") boolean dryRun) {
        this.jobRunner = jobRunner;
        this.sweepService = sweepService;
        this.staleAfter = staleAfter == null ? DEFAULT_STALE_AFTER : staleAfter;
        this.dryRun = dryRun;
    }

    @Scheduled(cron = "${catcheck.jobs.report-pdf-timeout.cron:0 */15 * * * *}",
            zone = "${catcheck.jobs.zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.report-pdf-timeout.lock-at-most-for:PT10M}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, dryRun, this::failStaleJobs);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome failStaleJobs(JobContext context) {
        Instant cutoff = context.startedAt().minus(staleAfter);

        if (context.dryRun()) {
            long stale = sweepService.countStale(cutoff);
            return JobOutcome.of((int) Math.min(stale, Integer.MAX_VALUE), 0, 0,
                    "dry-run: " + stale + " export_job qua han");
        }

        int inspected = 0;
        int closed = 0;
        int errors = 0;
        String firstError = null;

        while (inspected < MAX_ITEMS_PER_RUN) {
            int limit = Math.min(BATCH_SIZE, MAX_ITEMS_PER_RUN - inspected);
            List<UUID> staleIds = sweepService.findStaleIds(cutoff, limit);
            if (staleIds.isEmpty()) {
                break;
            }
            int closedInPage = 0;
            for (UUID jobId : staleIds) {
                inspected++;
                try {
                    if (sweepService.failIfStillStale(jobId, cutoff, context.startedAt())) {
                        closedInPage++;
                        log.warn("export_job {} ket qua {} - da dat FAILED", jobId, staleAfter);
                    }
                } catch (RuntimeException exception) {
                    errors++;
                    log.error("Khong dong duoc export_job {}", jobId, exception);
                    if (firstError == null) {
                        firstError = exception.getClass().getSimpleName();
                    }
                }
            }
            closed += closedInPage;
            // Trang vẫn đầy nhưng không đóng được dòng nào: vòng sau sẽ lấy đúng trang này (câu
            // SELECT lọc theo điều kiện, không theo cursor) nên dừng lại thay vì lặp vô hạn.
            if (staleIds.size() < limit || closedInPage == 0) {
                break;
            }
        }

        String summary = closed == 0 && errors == 0
                ? null
                : closed + " job qua han da dat FAILED"
                        + (errors == 0 ? "" : ", " + errors + " loi, loi dau tien: " + firstError);
        // itemsDeleted = 0: job này không xoá dòng nào, chỉ đổi trạng thái.
        return JobOutcome.of(inspected, 0, errors, summary);
    }
}
