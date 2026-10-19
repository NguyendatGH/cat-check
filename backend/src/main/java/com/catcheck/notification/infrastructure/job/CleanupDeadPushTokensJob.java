package com.catcheck.notification.infrastructure.job;

import com.catcheck.notification.application.DeadPushTokenCleanupService;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.ManualJobTrigger;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.cleanup-dead-push-tokens", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class CleanupDeadPushTokensJob implements ManualJobTrigger {

    public static final String JOB_NAME = JobProperties.CLEANUP_DEAD_PUSH_TOKENS;

    private static final Logger log = LoggerFactory.getLogger(CleanupDeadPushTokensJob.class);

    private final JobRunner jobRunner;
    private final DeadPushTokenCleanupService cleanup;
    private final JobProperties.JobSetting setting;

    public CleanupDeadPushTokensJob(JobRunner jobRunner,
                                    DeadPushTokenCleanupService cleanup,
                                    JobProperties jobProperties) {
        this.jobRunner = jobRunner;
        this.cleanup = cleanup;
        this.setting = jobProperties.cleanupDeadPushTokens();
    }

    @Scheduled(cron = "${catcheck.jobs.cleanup-dead-push-tokens.cron}", zone = "${catcheck.jobs.zone}")
    @SchedulerLock(
            name = JOB_NAME,
            lockAtMostFor = "${catcheck.jobs.cleanup-dead-push-tokens.lock-at-most-for}",
            lockAtLeastFor = "PT10S")
    public void run() {
        jobRunner.runScheduled(JOB_NAME, setting.dryRun(), this::purge);
    }

    JobOutcome purge(JobContext context) {
        DeadPushTokenCleanupService.Survey survey = cleanup.survey(context.startedAt());
        int eligible = (int) Math.min(survey.eligible(), Integer.MAX_VALUE);

        if (context.dryRun()) {
            return JobOutcome.of(eligible, 0, 0,
                    "dry-run: " + eligible + "/" + survey.total() + " dong du dieu kien xoa");
        }
        if (survey.exceedsSafetyThreshold()) {
            log.warn("CleanupDeadPushTokensJob dung vi nguong an toan 20%: {}/{} dong.",
                    survey.eligible(), survey.total());
            return JobOutcome.skippedThreshold(eligible,
                    "nguong an toan 20%: " + survey.eligible() + "/" + survey.total());
        }

        int deleted = 0;
        while (deleted < setting.maxItemsPerRun()) {
            int limit = Math.min(setting.batchSize(), setting.maxItemsPerRun() - deleted);
            int removed = cleanup.deleteBatch(context.startedAt(), limit);
            deleted += removed;
            if (removed < limit) {
                break;
            }
        }
        if (deleted > 0) {
            log.info("CleanupDeadPushTokensJob: xoa {} dang ky push het han.", deleted);
        }
        return new JobOutcome(
                com.catcheck.shared.job.JobRunStatus.SUCCESS, eligible, deleted, 0, null);
    }


    @Override
    public String jobName() {
        return JOB_NAME;
    }

    @Override
    public JobOutcome runOnce(JobContext context) {
        return purge(context);
    }
}
