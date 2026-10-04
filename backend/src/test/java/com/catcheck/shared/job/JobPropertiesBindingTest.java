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

/**
 * Bind thật khối {@code catcheck.jobs} của {@code application.yml} vào {@link JobProperties}.
 *
 * <p>Hai thứ test này bắt mà test đơn vị khác không bắt được:</p>
 * <ol>
 *   <li><b>Lệch tên khoá.</b> Cùng một khoá được đọc ở hai nơi — {@code @ConfigurationProperties}
 *       (relaxed binding, kebab-case) và placeholder {@code ${...}} trong {@code @Scheduled} /
 *       {@code @SchedulerLock} (khớp chuỗi tuyệt đối, KHÔNG relaxed). Gõ sai một khoá thì bean
 *       vẫn bind bằng giá trị mặc định còn scheduler ném
 *       {@code IllegalArgumentException: Could not resolve placeholder} lúc khởi động.</li>
 *   <li><b>Lệch với spec.</b> Cron và {@code lockAtMostFor} ở p12 §12.6 là "con số duy nhất
 *       trong toàn bộ SPEC" (§12.6.1 quy tắc 1); test khoá cứng đúng các con số đó.</li>
 * </ol>
 */
class JobPropertiesBindingTest {

    /**
     * Đúng các chuỗi placeholder xuất hiện trong {@code @Scheduled}/{@code @SchedulerLock} của
     * bảy job. Danh sách này phải khớp tuyệt đối — đó là toàn bộ lý do tồn tại của nó.
     */
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

        // p12 §12.6.2 — mỗi giờ phút :05, lockAtMostFor 50m.
        assertEquals("0 5 * * * *", properties.expireCreditBatches().cron());
        assertEquals(Duration.ofMinutes(50), properties.expireCreditBatches().lockAtMostFor());
        assertTrue(properties.expireCreditBatches().enabled());

        // p12 §12.6.2 — mỗi giờ phút :15, lockAtMostFor 50m.
        assertEquals("0 15 * * * *", properties.creditExpiringReminder().cron());
        assertEquals(Duration.ofMinutes(50), properties.creditExpiringReminder().lockAtMostFor());

        // p12 §12.6.6 — mỗi 10 phút, lockAtMostFor 5m.
        assertEquals("0 */10 * * * *", properties.jobHeartbeatCheck().cron());
        assertEquals(Duration.ofMinutes(5), properties.jobHeartbeatCheck().lockAtMostFor());
        assertTrue(properties.jobHeartbeatCheck().enabled());

        // p12 §12.6.3 nhóm B — mỗi 5 phút, lockAtMostFor 4m30s, LIMIT 200 (§12.5.5a).
        assertEquals("0 */5 * * * *", properties.sendDueReminders().cron());
        assertEquals(Duration.ofSeconds(270), properties.sendDueReminders().lockAtMostFor());
        assertEquals(200, properties.sendDueReminders().batchSize());
        assertTrue(properties.sendDueReminders().enabled());

        // p12 §12.6.3 nhóm B — mỗi 30 giây (cron 6 trường), lockAtMostFor 25s.
        assertEquals("*/30 * * * * *", properties.sendEmailOutbox().cron());
        assertEquals(Duration.ofSeconds(25), properties.sendEmailOutbox().lockAtMostFor());
        assertTrue(properties.sendEmailOutbox().enabled());

        // p12 §12.6.3 nhóm B — mỗi 15 phút, lockAtMostFor 14m.
        assertEquals("0 */15 * * * *", properties.retryFailedNotifications().cron());
        assertEquals(Duration.ofMinutes(14), properties.retryFailedNotifications().lockAtMostFor());
        assertTrue(properties.retryFailedNotifications().enabled());

        // p12 §12.6.3 nhóm B — Chủ Nhật 04:00, lockAtMostFor 30m.
        assertEquals("0 0 4 * * SUN", properties.cleanupDeadPushTokens().cron());
        assertEquals(Duration.ofMinutes(30), properties.cleanupDeadPushTokens().lockAtMostFor());
        assertTrue(properties.cleanupDeadPushTokens().enabled());
    }

    @Test
    void creditExpiringReminderIsEnabledNowThatTheOutboxAdapterExists() {
        // W2-B / H15.46: credit.infrastructure.notification.CreditExpiryNotificationAdapter đã
        // hiện thực CreditExpiryNotificationPort bằng notification::api, nên job không còn lý do
        // để tắt. Test trước đây khẳng định điều ngược lại và tự ghi "xoá test này khi có
        // adapter"; giữ lại ở chiều mới để một lần tắt nhầm trong cấu hình bị bắt ngay.
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
        // job_run.job_name la VARCHAR(64) (p4 §K3) va phai dung ten p12, khong viet tat.
        for (JobDescriptor descriptor : bind().scheduledJobs()) {
            assertTrue(descriptor.name().endsWith("Job"), descriptor.name());
            assertTrue(descriptor.name().length() <= 64, descriptor.name());
        }
    }
}
