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

@Component
@ConditionalOnProperty(prefix = "catcheck.jobs.send-due-reminders", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SendDueRemindersJob {

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
