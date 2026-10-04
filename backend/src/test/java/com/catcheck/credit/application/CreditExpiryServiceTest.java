package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Nghiệp vụ hết hạn credit (p5 R3, p12 §12.6.2) với cổng trong bộ nhớ.
 *
 * <p>Ba điều phải đúng cùng lúc, nếu không sổ cái lệch vĩnh viễn ({@code credit_ledger} là
 * append-only): dòng ledger loại {@code EXPIRE} đúng bằng phần chưa dùng, {@code remaining_amount}
 * về 0, {@code status = EXPIRED}. Thêm một điều nữa dễ bị bỏ sót:
 * {@code ref_type = JOB}/{@code ref_id = job_run.id} theo p4 §4.4.6 — nếu không, dòng
 * {@code EXPIRE} không truy ngược được về lần chạy nào sinh ra nó.</p>
 */
class CreditExpiryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-03T05:05:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final InMemoryCreditStore store = new InMemoryCreditStore();
    private final CreditExpiryService service =
            new CreditExpiryService(store, store, new UuidV7(CLOCK));

    private final UUID userId = UUID.randomUUID();
    private final UUID jobRunId = UUID.randomUUID();

    @Test
    void expiredBatchIsClosedAndLedgerRecordsTheUnusedAmount() {
        InMemoryCreditStore.Batch batch =
                store.addBatch(userId, NOW.minusSeconds(60), 4, 10);

        assertTrue(service.expireBatch(batch.id, jobRunId, NOW));

        assertEquals(0, batch.remainingAmount, "p5 R3: phan chua dung cua lo ve 0");
        assertEquals(CreditBatchStatus.EXPIRED, batch.status);

        List<LedgerEntry> expireRows = store.entriesOfType(CreditLedgerType.EXPIRE);
        assertEquals(1, expireRows.size(), "mot lo het han sinh dung mot dong ledger");
        LedgerEntry entry = expireRows.getFirst();
        assertEquals(-4, entry.amount(), "amount am, dung bang phan con lai (ck_credit_ledger_sign)");
        assertEquals(batch.id, entry.batchId());
        assertEquals(userId, entry.userId());
        assertEquals(CreditLedgerRefType.JOB, entry.refType(), "p4 §4.4.6: job nen ghi ref_type = JOB");
        assertEquals(jobRunId, entry.refId(), "ref_id phai la job_run.id");
        assertNull(entry.idempotencyKey(), "thao tac nen khong mang idempotency_key (p5 R8)");
        assertEquals(NOW, entry.createdAt());
    }

    @Test
    void balanceAfterExcludesTheBatchJustExpired() {
        store.addBatch(userId, NOW.minusSeconds(60), 4, 10);
        store.addBatch(userId, NOW.plusSeconds(86_400), 6, 6);
        UUID expiringId = store.batches.getFirst().id;

        service.expireBatch(expiringId, jobRunId, NOW);

        LedgerEntry entry = store.entriesOfType(CreditLedgerType.EXPIRE).getFirst();
        assertEquals(6, entry.balanceAfter(),
                "balance_after phan anh trang thai SAU giao dich (p5 R4): chi con lo chua het han");
    }

    @Test
    void secondRunOverTheSameBatchWritesNoDuplicateLedgerRow() {
        // Idempotency của p12 §12.6.2: "điều kiện remaining_amount > 0 làm lần chạy sau không
        // ghi ledger trùng".
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.minusSeconds(60), 4, 10);

        assertTrue(service.expireBatch(batch.id, jobRunId, NOW));
        assertFalse(service.expireBatch(batch.id, UUID.randomUUID(), NOW));

        assertEquals(1, store.entriesOfType(CreditLedgerType.EXPIRE).size());
    }

    @Test
    void batchThatIsStillLiveIsNotTouched() {
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.plusSeconds(3_600), 5, 5);

        assertFalse(service.expireBatch(batch.id, jobRunId, NOW));

        assertEquals(CreditBatchStatus.ACTIVE, batch.status);
        assertEquals(5, batch.remainingAmount);
        assertTrue(store.entries.isEmpty());
    }

    @Test
    void exhaustedBatchNeedsNoExpireRow() {
        // Lô đã tiêu hết trước hạn: remaining = 0 nên không có gì để thu hồi, và một dòng
        // EXPIRE với amount = 0 sẽ vi phạm ck_credit_ledger_amount.
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.minusSeconds(60), 3, 3);
        batch.remainingAmount = 0;

        assertFalse(service.expireBatch(batch.id, jobRunId, NOW));
        assertTrue(store.entries.isEmpty());
    }

    @Test
    void lockedRowIsSkippedNotWaitedOn() {
        // FOR UPDATE SKIP LOCKED: lô đang bị transaction khác giữ thì bỏ qua, gặp lại giờ sau.
        InMemoryCreditStore.Batch batch = store.addBatch(userId, NOW.minusSeconds(60), 4, 10);
        store.locked.add(batch.id);

        assertFalse(service.expireBatch(batch.id, jobRunId, NOW));
        assertEquals(CreditBatchStatus.ACTIVE, batch.status);
    }

    @Test
    void findDueBatchIdsAppliesTheSameFilterAndOrdersByExpiry() {
        store.addBatch(userId, NOW.minusSeconds(10), 1, 1);
        store.addBatch(userId, NOW.minusSeconds(1_000), 2, 2);
        store.addBatch(userId, NOW.plusSeconds(10), 3, 3);

        List<UUID> due = service.findDueBatchIds(NOW, 10);

        assertEquals(2, due.size(), "lo chua het han khong nam trong danh sach");
        assertEquals(store.batches.get(1).id, due.getFirst(), "sap theo expires_at tang dan");
        assertEquals(2, service.countDueBatches(NOW));
    }
}
