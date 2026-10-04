package com.catcheck.reminder.application;

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
 * <b>Nhóm B, p12 §12.6.3 — mỗi 5 phút ({@code 0 *&#47;5 * * * *}), {@code lockAtMostFor}
 * 4m30s.</b>
 *
 * <p>Quét {@code reminder} tới hạn, đẩy thông báo qua {@code notification::api} và tính lại
 * {@code next_run_at}. Trước gói việc này tính năng nhắc nhở có đủ 6 endpoint I1–I6 nhưng
 * KHÔNG có job nào gửi — người dùng đặt lịch xong thì không bao giờ nhận được gì.</p>
 *
 * <p><b>Ba lớp chống gửi trùng</b>, đúng p12 §12.5.5(a)(b)(c):</p>
 * <ol>
 *   <li>{@code @SchedulerLock} — hai instance không cùng bắt đầu một vòng quét.</li>
 *   <li>{@code SELECT … FOR UPDATE SKIP LOCKED} ở {@link DueReminderDispatcher#dispatch} —
 *       lớp bảo vệ thứ hai ở tầng dòng, cho trường hợp ShedLock nhả khoá vì một lần chạy
 *       vượt {@code lockAtMostFor}.</li>
 *   <li>{@code next_run_at} được đẩy về tương lai trong CÙNG transaction với việc ghi outbox,
 *       cộng {@code notification.dedupe_key UNIQUE} ở tầng DB.</li>
 * </ol>
 *
 * <p>Vòng lặp dừng khi trang trả về ít hơn {@code limit} (hết việc) hoặc khi cả một trang đầy
 * mà không xếp hàng được dòng nào — trang sau sẽ lấy lại đúng những dòng đó nên lặp tiếp là
 * vòng vô hạn (cùng lý do như {@code ExpireCreditBatchesJob}).</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.send-due-reminders", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SendDueRemindersJob {

    /** Đúng tên ở p12 §12.6.3 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.SEND_DUE_REMINDERS;

    private static final Logger log = LoggerFactory.getLogger(SendDueRemindersJob.class);

    private final JobRunner jobRunner;
    private final DueReminderDispatcher dispatcher;
    private final JobProperties.JobSetting setting;

    public SendDueRemindersJob(JobRunner jobRunner,
                               DueReminderDispatcher dispatcher,
                               JobProperties jobProperties) {
        this.jobRunner = jobRunner;
        this.dispatcher = dispatcher;
        this.setting = jobProperties.sendDueReminders();
    }

    @Scheduled(cron = "${catcheck.jobs.send-due-reminders.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.send-due-reminders.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::sendDue);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome sendDue(JobContext context) {
        if (context.dryRun()) {
            int due = dispatcher.findDue(context.startedAt(), setting.maxItemsPerRun()).size();
            return JobOutcome.of(due, 0, 0, "dry-run: " + due + " lich den han");
        }

        int inspected = 0;
        int queued = 0;
        int failed = 0;
        String firstError = null;

        while (inspected < setting.maxItemsPerRun()) {
            int limit = Math.min(setting.batchSize(), setting.maxItemsPerRun() - inspected);
            List<UUID> dueIds = dispatcher.findDue(context.startedAt(), limit);
            if (dueIds.isEmpty()) {
                break;
            }
            int queuedInPage = 0;
            for (UUID reminderId : dueIds) {
                inspected++;
                try {
                    if (dispatcher.dispatch(reminderId, context.startedAt())) {
                        queuedInPage++;
                    }
                } catch (RuntimeException exception) {
                    // p12 §12.6.3: "lỗi một reminder không chặn batch; đẩy sang outbox retry".
                    failed++;
                    log.error("Khong gui duoc nhac cho reminder {}", reminderId, exception);
                    if (firstError == null) {
                        firstError = exception.getClass().getSimpleName();
                    }
                }
            }
            queued += queuedInPage;
            if (dueIds.size() < limit || queuedInPage == 0) {
                break;
            }
        }

        String summary = firstError == null
                ? (queued == 0 ? null : queued + " nhac da xep hang")
                : queued + " nhac da xep hang, " + failed + " loi, loi dau tien: " + firstError;
        return JobOutcome.of(inspected, 0, failed, summary);
    }
}
