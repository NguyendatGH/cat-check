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

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.credit-expiring-reminder", name = "enabled",
        havingValue = "true")
public class CreditExpiringReminderJob {

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

    JobOutcome queueDueReminders(JobContext context) {
        if (context.dryRun()) {
            int due = 0;
            for (ExpiryReminderMilestone milestone : ExpiryReminderMilestone.values()) {
                due += reminderService.countDueBatches(milestone, context.startedAt());
            }
            return JobOutcome.of(due, 0, 0, "dry-run: " + due + " lo den moc nhac");
        }

        if (!reminderService.notificationAvailable()) {
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
                        failed++;
                        log.error("Khong day duoc nhac {} cho credit_batch {}",
                                milestone, batchId, exception);
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
        }

        String summary = firstError == null
                ? (queued == 0 ? null : queued + " thong bao da xep hang")
                : queued + " thong bao da xep hang, " + failed + " loi, loi dau tien: " + firstError;
        return JobOutcome.of(inspected, 0, failed, summary);
    }
}
