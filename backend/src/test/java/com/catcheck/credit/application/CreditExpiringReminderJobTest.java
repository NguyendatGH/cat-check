package com.catcheck.credit.application;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreditExpiringReminderJobTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:15:00Z");

    private final InMemoryCreditStore store = new InMemoryCreditStore();
    private final RecordingNotificationPort outbox = new RecordingNotificationPort();
    private final UUID userId = UUID.randomUUID();

    private CreditExpiringReminderJob job(CreditExpiryNotificationPort port) {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 15 * * * *", Duration.ofMinutes(50), 500, 5_000, false);
        JobProperties properties =
                new JobProperties(true, ZoneId.of("Asia/Ho_Chi_Minh"),
                        setting, setting, setting, setting, setting, setting, setting, setting);
        CreditExpiryReminderService service = new CreditExpiryReminderService(
                store, store, new SingletonObjectProvider<>(port));
        return new CreditExpiringReminderJob(null, service, properties);
    }

    private JobContext context(boolean dryRun) {
        return new JobContext(UUID.randomUUID(), CreditExpiringReminderJob.JOB_NAME, NOW, dryRun);
    }

    @Test
    void queuesBothMilestonesAndFlagsTheBatch() {
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.plus(Duration.ofHours(5)), 3, 10);

        JobOutcome outcome = job(outbox).queueDueReminders(context(false));

        assertEquals(JobRunStatus.SUCCESS, outcome.status());
        assertEquals(2, outcome.itemsProcessed());
        assertEquals(List.of(ExpiryReminderMilestone.T48H, ExpiryReminderMilestone.T6H),
                outbox.milestones);
        assertNotNull(batch.t48hNotifiedAt);
        assertNotNull(batch.t6hNotifiedAt);
        assertEquals("CREDIT_EXPIRING_T48H", outbox.milestones.getFirst().templateCode());
        assertEquals("CREDIT_EXPIRING_T6H", outbox.milestones.getLast().templateCode());
    }

    @Test
    void onlyTheFarMilestoneFiresWhenExpiryIsStillTwoDaysOut() {
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.plus(Duration.ofHours(30)), 3, 10);

        job(outbox).queueDueReminders(context(false));

        assertEquals(List.of(ExpiryReminderMilestone.T48H), outbox.milestones);
        assertNotNull(batch.t48hNotifiedAt);
        assertNull(batch.t6hNotifiedAt);
    }

    @Test
    void alreadyNotifiedBatchIsNotQueuedAgain() {
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.plus(Duration.ofHours(30)), 3, 10);
        batch.t48hNotifiedAt = Instant.EPOCH;

        JobOutcome outcome = job(outbox).queueDueReminders(context(false));

        assertEquals(0, outcome.itemsProcessed());
        assertTrue(outbox.milestones.isEmpty());
    }

    @Test
    void missingOutboxAdapterFailsOnceWithAReadableReason() {
        store.addBatch(userId, NOW.plus(Duration.ofHours(5)), 3, 10);

        JobOutcome outcome = job(null).queueDueReminders(context(false));

        assertEquals(JobRunStatus.FAILED, outcome.status());
        assertTrue(outcome.errorSummary().contains("CreditExpiryNotificationPort"));
        assertEquals(0, outcome.itemsProcessed());
    }

    @Test
    void dryRunCountsBothMilestonesWithoutQueueing() {
        store.addBatch(userId, NOW.plus(Duration.ofHours(5)), 3, 10);

        JobOutcome outcome = job(outbox).queueDueReminders(context(true));

        assertEquals(2, outcome.itemsProcessed());
        assertTrue(outbox.milestones.isEmpty());
        assertTrue(outcome.errorSummary().startsWith("dry-run:"));
    }

    private static final class RecordingNotificationPort implements CreditExpiryNotificationPort {

        final List<ExpiryReminderMilestone> milestones = new ArrayList<>();

        @Override
        public void queueExpiryReminder(UUID userId, UUID batchId, ExpiryReminderMilestone milestone,
                                        Instant expiresAt, int remainingAmount) {
            milestones.add(milestone);
        }
    }
}
