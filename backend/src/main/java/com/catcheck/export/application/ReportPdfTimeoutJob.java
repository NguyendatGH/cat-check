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

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.report-pdf-timeout", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ReportPdfTimeoutJob {

    public static final String JOB_NAME = "ReportPdfTimeoutJob";

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
            if (staleIds.size() < limit || closedInPage == 0) {
                break;
            }
        }

        String summary = closed == 0 && errors == 0
                ? null
                : closed + " job qua han da dat FAILED"
                        + (errors == 0 ? "" : ", " + errors + " loi, loi dau tien: " + firstError);
        return JobOutcome.of(inspected, 0, errors, summary);
    }
}
