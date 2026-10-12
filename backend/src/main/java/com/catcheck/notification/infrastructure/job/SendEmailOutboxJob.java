package com.catcheck.notification.infrastructure.job;

import com.catcheck.notification.application.EmailOutboxDispatcher;
import com.catcheck.notification.application.OutboxDispatchReport;
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
@ConditionalOnProperty(prefix = "catcheck.jobs.send-email-outbox", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SendEmailOutboxJob implements ManualJobTrigger {

    public static final String JOB_NAME = JobProperties.SEND_EMAIL_OUTBOX;

    private final JobRunner jobRunner;
    private final EmailOutboxDispatcher dispatcher;
    private final JobProperties.JobSetting setting;

    public SendEmailOutboxJob(JobRunner jobRunner,
                              EmailOutboxDispatcher dispatcher,
                              JobProperties jobProperties) {
        this.jobRunner = jobRunner;
        this.dispatcher = dispatcher;
        this.setting = jobProperties.sendEmailOutbox();
    }

    @Scheduled(cron = "${catcheck.jobs.send-email-outbox.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.send-email-outbox.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::dispatch);
    }

    JobOutcome dispatch(JobContext context) {
        if (context.dryRun()) {
            return JobOutcome.of(0, 0, 0, "dry-run: khong gui email");
        }
        OutboxDispatchReport report = dispatcher.dispatchPending();
        return JobOutcome.of(report.processed(), 0, report.failed(), summary(report));
    }

    private static String summary(OutboxDispatchReport report) {
        if (report.processed() == 0) {
            return null;
        }
        return "gui " + report.sent() + ", thu lai " + report.retried()
                + ", dead letter " + report.failed();
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
