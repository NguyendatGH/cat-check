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

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.retry-failed-notifications", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class RetryFailedNotificationsJob implements ManualJobTrigger {

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


    @Override
    public String jobName() {
        return JOB_NAME;
    }

    @Override
    public JobOutcome runOnce(JobContext context) {
        return dispatch(context);
    }
}
