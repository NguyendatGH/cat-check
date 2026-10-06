package com.catcheck.notification.infrastructure.job;

import com.catcheck.notification.application.OutboxDispatchReport;
import com.catcheck.notification.application.PushOutboxDispatcher;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.ManualJobTrigger;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * {@code RetryFailedNotificationsJob} — p12 §12.6.3: đẩy {@code notification_outbox} (push FCM)
 * trạng thái {@code PENDING}, xử lý response FCM (thu hồi token {@code UNREGISTERED}/
 * {@code INVALID_ARGUMENT}, backoff lỗi tạm), <b>mỗi 15 phút</b>, {@code lockAtMostFor = 14m}.
 *
 * <p>Tên job giữ nguyên như p12 §12.6.3 đặt, dù việc nó làm là "đẩy hàng chờ" chứ không chỉ
 * "thử lại cái đã hỏng" — p12 là danh mục job duy nhất của dự án và §12.6.1 quy tắc 1 cấm part
 * khác đặt tên khác cho cùng một job; đổi tên ở code sẽ làm runbook p18, dashboard admin p14 và
 * dòng {@code job_run} không tra được nhau.</p>
 *
 * <p><b>W2-B (H15.73): đã chuyển lên nền {@code shared/job}</b> nên từ nay có dòng
 * {@code job_run} mỗi lần chạy (p12 §12.6.1 quy tắc 4, §12.8.2). Cron, {@code lockAtMostFor}
 * và toàn bộ hành vi backoff/thu hồi token của {@code PushOutboxDispatcher} giữ nguyên; công
 * tắc chuyển sang {@code catcheck.jobs.retry-failed-notifications.enabled}.</p>
 */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.retry-failed-notifications", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RetryFailedNotificationsJob implements ManualJobTrigger {

    /** Đúng tên ở p12 §12.6.3 — {@code job_run.job_name} không viết tắt. */
    public static final String JOB_NAME = JobProperties.RETRY_FAILED_NOTIFICATIONS;

    private final JobRunner jobRunner;
    private final PushOutboxDispatcher dispatcher;
    private final JobProperties.JobSetting setting;

    public RetryFailedNotificationsJob(JobRunner jobRunner,
                                       PushOutboxDispatcher dispatcher,
                                       JobProperties jobProperties) {
        this.jobRunner = jobRunner;
        this.dispatcher = dispatcher;
        this.setting = jobProperties.retryFailedNotifications();
    }

    @Scheduled(cron = "${catcheck.jobs.retry-failed-notifications.cron}",
            zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.retry-failed-notifications.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::dispatch);
    }

    /** Thân job — tách khỏi {@link #run()} để test gọi thẳng, không cần scheduler. */
    JobOutcome dispatch(JobContext context) {
        if (context.dryRun()) {
            return JobOutcome.of(0, 0, 0, "dry-run: khong day push");
        }
        OutboxDispatchReport report = dispatcher.dispatchPending();
        return JobOutcome.of(report.processed(), 0, report.failed(), summary(report));
    }

    private static String summary(OutboxDispatchReport report) {
        if (report.processed() == 0) {
            return null;
        }
        return "gui " + report.sent() + ", thu lai " + report.retried()
                + ", dead letter " + report.failed() + ", bo qua " + report.skipped();
    }

    /* ------------------------------------------------ ManualJobTrigger (p8 L65) */

    @Override
    public String jobName() {
        return JOB_NAME;
    }

    /**
     * L65 {@code POST /admin/jobs/{jobName}/run} goi than job, KHONG goi {@link #run()}:
     * {@code run()} tu mo mot dong {@code job_run} qua {@code JobRunner}, nen goi no o day
     * se de lai HAI dong cho mot lan admin bam — p12 §12.6.1 quy tac 4 chot "dung mot dong moi
     * lan chay". {@code ManualJobLauncher} la noi boc {@code JobRunner} voi
     * {@code trigger_type = MANUAL}.
     */
    @Override
    public JobOutcome runOnce(JobContext context) {
        return dispatch(context);
    }
}
