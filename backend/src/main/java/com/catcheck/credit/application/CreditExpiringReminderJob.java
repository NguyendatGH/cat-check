package com.catcheck.credit.application;

import com.catcheck.credit.domain.ExpiryReminderMilestone;
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
 * <b>Nhóm A, p12 §12.6.2 — mỗi giờ phút :15 ({@code 0 15 * * * *}), {@code lockAtMostFor} 50m.</b>
 *
 * <p>Quét lô sắp hết hạn ở hai mốc <b>T-48h</b> và <b>T-6h</b> (còn {@code remaining_amount > 0})
 * và đẩy {@code CREDIT_EXPIRING_T48H}/{@code CREDIT_EXPIRING_T6H} vào outbox. Idempotency theo
 * {@code credit_batch.t48h_notified_at}/{@code t6h_notified_at} (p12 §12.6.2).</p>
 *
 * <p><b>Job này mặc định TẮT</b> ở {@code application.yml}. Lý do không phải e dè: module
 * {@code notification} hiện chưa có cổng outbox nào để
 * {@link com.catcheck.credit.application.spi.CreditExpiryNotificationPort} gắn vào (nó chỉ có
 * {@code EmailSender} gửi trực tiếp, mà p12 §12.6.1 quy tắc 8 cấm job gửi trực tiếp trong thread
 * của mình). Bật job khi chưa có adapter chỉ sinh một dòng {@code job_run} {@code FAILED} mỗi
 * giờ, làm loãng đúng cái cảnh báo mà cột "Cảnh báo khi fail" của p12 muốn dùng. Xem
 * {@code handoffs.md} H15.e; bật lại bằng
 * {@code catcheck.jobs.credit-expiring-reminder.enabled=true} ngay khi có adapter.</p>
 *
 * <p>Thứ tự quét là <b>T-48h trước, T-6h sau</b>: một lô vào thẳng cửa sổ 6 giờ (ví dụ gói ngắn
 * hạn, hoặc job vừa được bật lại sau sự cố) phải nhận cả hai thông báo trong cùng lần chạy chứ
 * không được bỏ mốc xa hơn — mốc T-48h là mốc duy nhất còn kịp để người dùng làm gì đó.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.credit-expiring-reminder", name = "enabled",
        havingValue = "true")
public class CreditExpiringReminderJob {

    /** Đúng tên ở p12 §12.6.2 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.CREDIT_EXPIRING_REMINDER;

    private static final Logger log = LoggerFactory.getLogger(CreditExpiringReminderJob.class);

    private final JobRunner jobRunner;
    private final CreditExpiryReminderService reminderService;
    private final JobProperties.JobSetting setting;

    public CreditExpiringReminderJob(
            JobRunner jobRunner,
            CreditExpiryReminderService reminderService,
            JobProperties jobProperties
    ) {
        this.jobRunner = jobRunner;
        this.reminderService = reminderService;
        this.setting = jobProperties.creditExpiringReminder();
    }

    @Scheduled(cron = "${catcheck.jobs.credit-expiring-reminder.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.credit-expiring-reminder.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::queueDueReminders);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome queueDueReminders(JobContext context) {
        if (context.dryRun()) {
            int due = 0;
            for (ExpiryReminderMilestone milestone : ExpiryReminderMilestone.values()) {
                due += reminderService.countDueBatches(milestone, context.startedAt());
            }
            return JobOutcome.of(due, 0, 0, "dry-run: " + due + " lo den moc nhac");
        }

        if (!reminderService.notificationAvailable()) {
            // Thất bại ngay, một dòng, có lý do rõ — thay vì quét cả bảng rồi lỗi ở từng lô.
            return JobOutcome.failed(
                    "Thieu adapter CreditExpiryNotificationPort (H15.e) — khong the day thong bao vao outbox");
        }

        int queued = 0;
        int failed = 0;
        int inspected = 0;
        String firstError = null;

        for (ExpiryReminderMilestone milestone : ExpiryReminderMilestone.values()) {
            int inspectedForMilestone = 0;
            while (inspectedForMilestone < setting.maxItemsPerRun()) {
                int limit = Math.min(setting.batchSize(), setting.maxItemsPerRun() - inspectedForMilestone);
                List<UUID> dueIds = reminderService.findDueBatchIds(milestone, context.startedAt(), limit);
                if (dueIds.isEmpty()) {
                    break;
                }
                int queuedInPage = 0;
                for (UUID batchId : dueIds) {
                    inspectedForMilestone++;
                    inspected++;
                    try {
                        if (reminderService.queueReminder(batchId, milestone, context.startedAt())) {
                            queuedInPage++;
                        }
                    } catch (RuntimeException exception) {
                        // p12 §12.6.2: "lỗi một thông báo không chặn batch khác; retry qua outbox".
                        failed++;
                        log.error("Khong day duoc nhac {} cho credit_batch {}",
                                milestone, batchId, exception);
                        if (firstError == null) {
                            firstError = exception.getClass().getSimpleName();
                        }
                    }
                }
                queued += queuedInPage;
                // Trang đầy mà không xếp được gì ⇒ cờ notified không đổi ⇒ vòng sau lấy đúng
                // trang này. Dừng để không lặp vô hạn (cùng lý do như ExpireCreditBatchesJob).
                if (dueIds.size() < limit || queuedInPage == 0) {
                    break;
                }
            }
        }

        String summary = firstError == null
                ? (queued == 0 ? null : queued + " thong bao da xep hang")
                : queued + " thong bao da xep hang, " + failed + " loi, loi dau tien: " + firstError;
        return JobOutcome.of(inspected, 0, failed, summary);
    }
}
