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

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.expire-credit-batches", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ExpireCreditBatchesJob {

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

    JobOutcome expireDueBatches(JobContext context) {
        if (context.dryRun()) {
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
            if (expiredInPage == 0) {
                break;
            }
        }

        String summary = firstError == null
                ? (expired == 0 ? null : expired + " lo da dong")
                : expired + " lo da dong, " + failed + " lo loi, loi dau tien: " + firstError;
        return JobOutcome.of(inspected, 0, failed, summary);
    }
}
