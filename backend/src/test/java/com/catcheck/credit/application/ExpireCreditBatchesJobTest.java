package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.shared.id.UuidV7;
import com.catcheck.shared.job.JobContext;
import com.catcheck.shared.job.JobOutcome;
import com.catcheck.shared.job.JobProperties;
import com.catcheck.shared.job.JobRunStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thân {@code ExpireCreditBatchesJob} (p12 §12.6.2 nhóm A) — gọi thẳng, không qua scheduler.
 *
 * <p>Kiểm ba hành vi mà spec nêu đích danh: xử lý theo lô có {@code LIMIT}, một lô lỗi không
 * chặn lô khác, và {@code dry_run} chỉ đếm.</p>
 */
class ExpireCreditBatchesJobTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryCreditStore store = new InMemoryCreditStore();
    private final UUID userId = UUID.randomUUID();

    private ExpireCreditBatchesJob job(int batchSize, int maxItemsPerRun) {
        JobProperties.JobSetting setting = new JobProperties.JobSetting(
                true, "0 5 * * * *", Duration.ofMinutes(50), batchSize, maxItemsPerRun, false);
        JobProperties properties =
                new JobProperties(true, ZoneId.of("Asia/Ho_Chi_Minh"),
                        setting, setting, setting, setting, setting, setting, setting, setting);
        CreditExpiryService service = new CreditExpiryService(store, store, new UuidV7(CLOCK));
        // JobRunner null: test gọi thẳng thân job, không đi qua khuôn chạy chung (đã có
        // JobRunnerTest lo phần đó) — tách hai mối quan tâm thay vì dựng cả hai mỗi lần.
        return new ExpireCreditBatchesJob(null, service, properties);
    }

    private JobContext context(boolean dryRun) {
        return new JobContext(UUID.randomUUID(), ExpireCreditBatchesJob.JOB_NAME, NOW, dryRun);
    }

    @Test
    void expiresEveryDueBatchAcrossSeveralPages() {
        for (int i = 0; i < 7; i++) {
            store.addBatch(userId, NOW.minusSeconds(60L * (i + 1)), 2, 5);
        }
        store.addBatch(userId, NOW.plusSeconds(3_600), 5, 5);

        JobOutcome outcome = job(3, 100).expireDueBatches(context(false));

        assertEquals(JobRunStatus.SUCCESS, outcome.status());
        assertEquals(7, outcome.itemsProcessed());
        assertEquals(0, outcome.itemsFailed());
        assertEquals(0, outcome.itemsDeleted(), "job nay dong lo, khong xoa dong nao (p5 R3)");
        assertEquals(7, store.entriesOfType(CreditLedgerType.EXPIRE).size());
        assertEquals(1, store.batches.stream()
                .filter(b -> b.status == CreditBatchStatus.ACTIVE).count());
    }

    @Test
    void oneFailingBatchDoesNotBlockTheOthers() {
        // p12 §12.6.2: "Mỗi batch một transaction riêng; lỗi 1 batch không chặn batch khác".
        store.addBatch(userId, NOW.minusSeconds(300), 2, 5);
        InMemoryCreditStore.Batch broken = store.addBatch(userId, NOW.minusSeconds(200), 2, 5);
        store.addBatch(userId, NOW.minusSeconds(100), 2, 5);
        store.failing.add(broken.id);

        JobOutcome outcome = job(10, 100).expireDueBatches(context(false));

        assertEquals(JobRunStatus.PARTIAL, outcome.status());
        assertEquals(3, outcome.itemsProcessed());
        assertEquals(1, outcome.itemsFailed());
        assertEquals(2, store.entriesOfType(CreditLedgerType.EXPIRE).size());
        assertEquals(CreditBatchStatus.ACTIVE, broken.status);
        assertTrue(outcome.errorSummary().contains("IllegalStateException"));
    }

    @Test
    void dryRunOnlyCountsAndChangesNothing() {
        store.addBatch(userId, NOW.minusSeconds(60), 2, 5);
        store.addBatch(userId, NOW.minusSeconds(30), 3, 5);

        JobOutcome outcome = job(10, 100).expireDueBatches(context(true));

        assertEquals(2, outcome.itemsProcessed());
        assertTrue(store.entries.isEmpty(), "dry-run khong duoc ghi ledger (p15 REQ-RET-01)");
        assertTrue(store.batches.stream().allMatch(b -> b.status == CreditBatchStatus.ACTIVE));
        assertTrue(outcome.errorSummary().startsWith("dry-run:"));
    }

    @Test
    void stopsAtMaxItemsPerRunSoTheRunStaysWithinItsLock() {
        for (int i = 0; i < 10; i++) {
            store.addBatch(userId, NOW.minusSeconds(60L * (i + 1)), 2, 5);
        }

        JobOutcome outcome = job(2, 4).expireDueBatches(context(false));

        assertEquals(4, outcome.itemsProcessed());
        assertEquals(4, store.entriesOfType(CreditLedgerType.EXPIRE).size());
    }

    @Test
    void aFullPageOfLockedRowsDoesNotLoopForever() {
        // Trang đầy mà không đóng được lô nào ⇒ câu SELECT sẽ trả đúng trang đó mãi. Job phải
        // dừng và để giờ sau thử lại, thay vì quay vòng tới khi chạm lockAtMostFor.
        for (int i = 0; i < 4; i++) {
            InMemoryCreditStore.Batch batch =
                    store.addBatch(userId, NOW.minusSeconds(60L * (i + 1)), 2, 5);
            store.locked.add(batch.id);
        }

        JobOutcome outcome = job(2, 100).expireDueBatches(context(false));

        assertEquals(2, outcome.itemsProcessed(), "dung sau trang dau khong dong duoc gi");
        assertTrue(store.entries.isEmpty());
    }
}
