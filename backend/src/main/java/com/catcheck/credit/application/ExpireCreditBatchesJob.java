package com.catcheck.credit.application;

import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * <b>Nhóm A, p12 §12.6.2 — mỗi giờ phút :05 ({@code 0 5 * * * *}), {@code lockAtMostFor} 50m.</b>
 *
 * <p>Đóng {@code credit_batch} có {@code expires_at <= now} và {@code remaining_amount > 0}:
 * ghi {@code credit_ledger(EXPIRE)}, {@code remaining_amount = 0}, {@code status = EXPIRED}
 * (p5 R3). Nghiệp vụ nằm ở {@link CreditExpiryService}; lớp này chỉ lo lịch, khoá và nhật ký.</p>
 *
 * <p><b>Vì sao mỗi giờ mà không mỗi ngày:</b> p5 R3 chốt {@code expires_at} chính xác tới giờ,
 * nên một job chạy đêm sẽ để credit đã hết hạn tiêu được thêm tới 23 giờ. Ngược lại, giữa hai
 * lần chạy vẫn luôn tồn tại lô {@code status = 'ACTIVE'} mà {@code expires_at} đã qua — đó là lý
 * do đường FEFO <b>không</b> tin vào {@code status} một mình mà luôn kèm
 * {@code expires_at > now} (bất biến I3, p17 C2).</p>
 *
 * <p><b>Ba lớp chống chạy trùng</b> theo p12 §12.6.1 quy tắc 3: (a) ShedLock quanh cả lần chạy,
 * (b) {@code FOR UPDATE SKIP LOCKED} ở từng dòng trong {@link CreditExpiryService#expireBatch},
 * (c) bộ lọc {@code remaining_amount > 0} khiến lần chạy thứ hai không ghi ledger trùng. Lớp (b)
 * và (c) hoạt động độc lập với ShedLock, nên một lần chạy vượt {@code lockAtMostFor} vẫn không
 * sinh dòng ledger thừa.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.expire-credit-batches", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpireCreditBatchesJob {

    /** Đúng tên ở p12 §12.6.2 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.EXPIRE_CREDIT_BATCHES;

    private static final Logger log = LoggerFactory.getLogger(ExpireCreditBatchesJob.class);

    private final JobRunner jobRunner;
    private final CreditExpiryService expiryService;
    private final JobProperties.JobSetting setting;

    public ExpireCreditBatchesJob(
            JobRunner jobRunner,
            CreditExpiryService expiryService,
            JobProperties jobProperties
    ) {
        this.jobRunner = jobRunner;
        this.expiryService = expiryService;
        this.setting = jobProperties.expireCreditBatches();
    }

    @Scheduled(cron = "${catcheck.jobs.expire-credit-batches.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.expire-credit-batches.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::expireDueBatches);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome expireDueBatches(JobContext context) {
        if (context.dryRun()) {
            // p15 REQ-RET-01: chỉ đếm, không ghi. Vẫn ghi một dòng job_run (p4 §K3).
            int due = expiryService.countDueBatches(context.startedAt());
            return JobOutcome.of(due, 0, 0, "dry-run: " + due + " lo du dieu kien het han");
        }

        int expired = 0;
        int failed = 0;
        int inspected = 0;
        String firstError = null;

        while (inspected < setting.maxItemsPerRun()) {
            int limit = Math.min(setting.batchSize(), setting.maxItemsPerRun() - inspected);
            List<UUID> dueIds = expiryService.findDueBatchIds(context.startedAt(), limit);
            if (dueIds.isEmpty()) {
                break;
            }
            int expiredInPage = 0;
            for (UUID batchId : dueIds) {
                inspected++;
                try {
                    if (expiryService.expireBatch(batchId, context.runId(), context.startedAt())) {
                        expiredInPage++;
                    }
                } catch (RuntimeException exception) {
                    // p12 §12.6.2: "lỗi 1 batch không chặn batch khác; log kèm batch_id".
                    failed++;
                    log.error("Khong het han duoc credit_batch {}", batchId, exception);
                    if (firstError == null) {
                        firstError = exception.getClass().getSimpleName();
                    }
                }
            }
            expired += expiredInPage;
            if (dueIds.size() < limit) {
                break;
            }
            // Trang vẫn đầy nhưng KHÔNG đóng được lô nào trong trang đó: mọi lô đều lỗi hoặc
            // đang bị khoá, nên vòng sau sẽ lấy đúng trang này và lặp vô hạn (câu SELECT lọc
            // theo điều kiện, không theo cursor). Dừng và để lần chạy sau — giờ kế tiếp — thử lại.
            if (expiredInPage == 0) {
                break;
            }
        }

        String summary = firstError == null
                ? (expired == 0 ? null : expired + " lo da dong")
                : expired + " lo da dong, " + failed + " lo loi, loi dau tien: " + firstError;
        // itemsDeleted = 0: job này KHÔNG xoá dòng nào (p5 R3 giữ lô làm bằng chứng đối soát),
        // nó chỉ đóng lô. Cột items_deleted của p4 §K3 dành cho job retention.
        return JobOutcome.of(inspected, 0, failed, summary);
    }
}
