package com.catcheck.shared.job;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

@ConfigurationProperties(prefix = "catcheck.jobs")
public record JobProperties(

        @DefaultValue("true") boolean enabled,
        @DefaultValue("Asia/Ho_Chi_Minh") ZoneId zone,
        @DefaultValue JobSetting expireCreditBatches,
        @DefaultValue JobSetting creditExpiringReminder,
        @DefaultValue JobSetting jobHeartbeatCheck,
        @DefaultValue JobSetting sendDueReminders,
        @DefaultValue JobSetting sendEmailOutbox,
        @DefaultValue JobSetting retryFailedNotifications,
        @DefaultValue JobSetting cleanupDeadPushTokens,
        @DefaultValue JobSetting dsarExportCleanup
) {

    public static final String EXPIRE_CREDIT_BATCHES = "ExpireCreditBatchesJob";

    public static final String CREDIT_EXPIRING_REMINDER = "CreditExpiringReminderJob";

    public static final String JOB_HEARTBEAT_CHECK = "JobHeartbeatCheckJob";

    public static final String SEND_DUE_REMINDERS = "SendDueRemindersJob";

    public static final String SEND_EMAIL_OUTBOX = "SendEmailOutboxJob";

    public static final String RETRY_FAILED_NOTIFICATIONS = "RetryFailedNotificationsJob";

    public static final String CLEANUP_DEAD_PUSH_TOKENS = "CleanupDeadPushTokensJob";
    public static final String DSAR_EXPORT_CLEANUP = "DsarExportCleanupJob";

    public List<JobDescriptor> scheduledJobs() {
        return List.of(
                new JobDescriptor(EXPIRE_CREDIT_BATCHES, expireCreditBatches.cron(), zone,
                        enabled && expireCreditBatches.enabled()),
                new JobDescriptor(CREDIT_EXPIRING_REMINDER, creditExpiringReminder.cron(), zone,
                        enabled && creditExpiringReminder.enabled()),
                new JobDescriptor(JOB_HEARTBEAT_CHECK, jobHeartbeatCheck.cron(), zone,
                        enabled && jobHeartbeatCheck.enabled()),
                new JobDescriptor(SEND_DUE_REMINDERS, sendDueReminders.cron(), zone,
                        enabled && sendDueReminders.enabled()),
                new JobDescriptor(SEND_EMAIL_OUTBOX, sendEmailOutbox.cron(), zone,
                        enabled && sendEmailOutbox.enabled()),
                new JobDescriptor(RETRY_FAILED_NOTIFICATIONS, retryFailedNotifications.cron(), zone,
                        enabled && retryFailedNotifications.enabled()),
                new JobDescriptor(CLEANUP_DEAD_PUSH_TOKENS, cleanupDeadPushTokens.cron(), zone,
                        enabled && cleanupDeadPushTokens.enabled()),
                new JobDescriptor(DSAR_EXPORT_CLEANUP, dsarExportCleanup.cron(), zone,
                        enabled && dsarExportCleanup.enabled()));
    }

    public record JobSetting(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 0 * * * *") String cron,
            @DefaultValue("PT50M") Duration lockAtMostFor,
            @DefaultValue("500") int batchSize,
            @DefaultValue("5000") int maxItemsPerRun,
            @DefaultValue("false") boolean dryRun
    ) {

        public JobSetting {
            if (batchSize <= 0) {
                throw new IllegalArgumentException("catcheck.jobs.*.batch-size phải > 0: " + batchSize);
            }
            if (maxItemsPerRun <= 0) {
                throw new IllegalArgumentException(
                        "catcheck.jobs.*.max-items-per-run phải > 0: " + maxItemsPerRun);
            }
        }
    }
}
