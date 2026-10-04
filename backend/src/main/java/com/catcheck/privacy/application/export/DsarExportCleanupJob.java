package com.catcheck.privacy.application.export;

import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.application.JobRunner;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Removes expired DSAR archives and records the cleanup in the shared job ledger. */
@Component
@ConditionalOnProperty(prefix = "catcheck.jobs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DsarExportCleanupJob {
    public static final String JOB_NAME = "DsarExportCleanupJob";
    private final JobRunner runner;
    private final DataExportJobService exports;

    public DsarExportCleanupJob(JobRunner runner, DataExportJobService exports) {
        this.runner = runner;
        this.exports = exports;
    }

    @Scheduled(cron = "${catcheck.jobs.dsar-export-cleanup.cron:0 15 * * * *}", zone = "${catcheck.jobs.zone:Asia/Ho_Chi_Minh}")
    @SchedulerLock(name = JOB_NAME, lockAtMostFor = "${catcheck.jobs.dsar-export-cleanup.lock-at-most-for:PT10M}", lockAtLeastFor = "PT10S")
    public void run() {
        runner.runScheduled(JOB_NAME, false, context -> {
            int cleaned = exports.cleanupExpired(500);
            return JobOutcome.of(cleaned, cleaned, 0, "expired DSAR archives removed");
        });
    }
}
