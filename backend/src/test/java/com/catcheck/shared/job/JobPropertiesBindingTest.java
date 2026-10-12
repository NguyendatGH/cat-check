package com.catcheck.shared.job;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPropertiesBindingTest {

    private static final List<String> PLACEHOLDER_KEYS = List.of(
            "catcheck.jobs.zone",
            "catcheck.jobs.expire-credit-batches.cron",
            "catcheck.jobs.expire-credit-batches.lock-at-most-for",
            "catcheck.jobs.expire-credit-batches.enabled",
            "catcheck.jobs.credit-expiring-reminder.cron",
            "catcheck.jobs.credit-expiring-reminder.lock-at-most-for",
            "catcheck.jobs.credit-expiring-reminder.enabled",
            "catcheck.jobs.job-heartbeat-check.cron",
            "catcheck.jobs.job-heartbeat-check.lock-at-most-for",
            "catcheck.jobs.job-heartbeat-check.enabled",
            "catcheck.jobs.send-due-reminders.cron",
            "catcheck.jobs.send-due-reminders.lock-at-most-for",
            "catcheck.jobs.send-due-reminders.enabled",
            "catcheck.jobs.send-email-outbox.cron",
            "catcheck.jobs.send-email-outbox.lock-at-most-for",
            "catcheck.jobs.send-email-outbox.enabled",
            "catcheck.jobs.retry-failed-notifications.cron",
            "catcheck.jobs.retry-failed-notifications.lock-at-most-for",
            "catcheck.jobs.retry-failed-notifications.enabled",
            "catcheck.jobs.cleanup-dead-push-tokens.cron",
            "catcheck.jobs.cleanup-dead-push-tokens.lock-at-most-for",
            "catcheck.jobs.cleanup-dead-push-tokens.enabled");

    private final PropertySource<?> applicationYml = loadApplicationYml();

    private static PropertySource<?> loadApplicationYml() {
        try {
            return new YamlPropertySourceLoader()
                    .load("application.yml", new ClassPathResource("application.yml"))
                    .getFirst();
        } catch (IOException e) {
            throw new IllegalStateException("Khong doc duoc application.yml", e);
        }
    }

    private JobProperties bind() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(applicationYml);
        return new Binder(ConfigurationPropertySources.from(sources))
                .bind("catcheck.jobs", JobProperties.class)
                .orElseThrow(() -> new IllegalStateException("application.yml thieu khoi catcheck.jobs"));
    }

    @Test
    void everyPlaceholderKeyUsedByTheSchedulerExistsInApplicationYml() {
        for (String key : PLACEHOLDER_KEYS) {
            assertNotNull(applicationYml.getProperty(key),
                    "application.yml thieu khoa " + key + " — @Scheduled/@SchedulerLock se khong"
                            + " giai duoc placeholder va app khong khoi dong");
        }
    }

    @Test
    void schedulesMatchPart12() {
        JobProperties properties = bind();

        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), properties.zone(),
                "p12 §12.6.1 quy tac 1: lich ghi bang gio ICT");
        assertTrue(properties.enabled());

        assertEquals("0 5 * * * *", properties.expireCreditBatches().cron());
        assertEquals(Duration.ofMinutes(50), properties.expireCreditBatches().lockAtMostFor());
        assertTrue(properties.expireCreditBatches().enabled());

        assertEquals("0 15 * * * *", properties.creditExpiringReminder().cron());
        assertEquals(Duration.ofMinutes(50), properties.creditExpiringReminder().lockAtMostFor());

        assertEquals("0 */10 * * * *", properties.jobHeartbeatCheck().cron());
        assertEquals(Duration.ofMinutes(5), properties.jobHeartbeatCheck().lockAtMostFor());
        assertTrue(properties.jobHeartbeatCheck().enabled());

        assertEquals("0 */5 * * * *", properties.sendDueReminders().cron());
        assertEquals(Duration.ofSeconds(270), properties.sendDueReminders().lockAtMostFor());
        assertEquals(200, properties.sendDueReminders().batchSize());
        assertTrue(properties.sendDueReminders().enabled());

        assertEquals("*/30 * * * * *", properties.sendEmailOutbox().cron());
        assertEquals(Duration.ofSeconds(25), properties.sendEmailOutbox().lockAtMostFor());
        assertTrue(properties.sendEmailOutbox().enabled());

        assertEquals("0 */15 * * * *", properties.retryFailedNotifications().cron());
        assertEquals(Duration.ofMinutes(14), properties.retryFailedNotifications().lockAtMostFor());
        assertTrue(properties.retryFailedNotifications().enabled());

        assertEquals("0 0 4 * * SUN", properties.cleanupDeadPushTokens().cron());
        assertEquals(Duration.ofMinutes(30), properties.cleanupDeadPushTokens().lockAtMostFor());
        assertTrue(properties.cleanupDeadPushTokens().enabled());
    }

    @Test
    void creditExpiringReminderIsEnabledNowThatTheOutboxAdapterExists() {
        assertTrue(bind().creditExpiringReminder().enabled());
    }

    @Test
    void cronExpressionsParse() {
        for (JobDescriptor descriptor : bind().scheduledJobs()) {
            assertNotNull(org.springframework.scheduling.support.CronExpression.parse(descriptor.cron()),
                    "cron khong hop le: " + descriptor.name());
        }
    }

    @Test
    void jobNamesFitTheJobRunColumn() {
        for (JobDescriptor descriptor : bind().scheduledJobs()) {
            assertTrue(descriptor.name().endsWith("Job"), descriptor.name());
            assertTrue(descriptor.name().length() <= 64, descriptor.name());
        }
    }
}
