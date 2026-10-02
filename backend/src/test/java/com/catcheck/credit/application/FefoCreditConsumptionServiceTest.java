package com.catcheck.credit.application;

import com.catcheck.credit.api.CreditConsumption;
import com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand;
import com.catcheck.credit.api.CreditConsumption.CreditRefundCommand;
import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.Entitlement;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * FEFO với cổng trong bộ nhớ — kiểm chứng thuật toán mà không cần Testcontainers.
 *
 * <p>Ba kịch bản then chốt: (1) trừ từ lô sắp hết hạn TRƯỚC kể cả khi lô đó nằm sau thứ tự
 * chèn; (2) thiếu credit ném {@code CREDIT_INSUFFICIENT} kèm số khả dụng; (3) cùng
 * {@code idempotencyKey} gọi hai lần chỉ trừ một lần (p5 R8).</p>
 */
class FefoCreditConsumptionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-27T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final FakeLedgerPort ledger = new FakeLedgerPort();
    private final FakeEntitlementPort entitlements = new FakeEntitlementPort();
    private final UuidV7 uuidV7 = new UuidV7(CLOCK);

    private FefoCreditConsumptionService service() {
        return new FefoCreditConsumptionService(ledger, entitlements, uuidV7, CLOCK);
    }

    private CreditBatchSnapshot batch(UUID id, Instant expiresAt, int remaining, int initial) {
        return new CreditBatchSnapshot(id, expiresAt, remaining, initial);
    }

    /** Entitlement có quyền ghi — bắt buộc nếu không consume bị chặn ở bước requireWriteAccess. */
    private Entitlement writeAccess(UUID userId) {
        return new Entitlement(userId, "PLUS", 1, com.catcheck.credit.domain.PlanFeatures.none(),
                NOW.plusSeconds(86_400), 0, NOW);
    }

    @Test
    void consumesFromSoonestExpiringBatchFirst() {
        UUID userId = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        UUID sooner = UUID.randomUUID();
        // Lô "sau" hết hạn được khai báo TRƯỚC — FEFO phải chọn lô "sớm" hết hạn trước.
        ledger.batches.add(batch(later, NOW.plusSeconds(86_400), 5, 5));
        ledger.batches.add(batch(sooner, NOW.plusSeconds(3_600), 5, 5));
        entitlements.row.put(userId, writeAccess(userId));

        CreditConsumption.CreditCharge charge = service().consume(
                userId, new CreditConsumeCommand(3, "key-1", CreditLedgerRefType.SCAN, UUID.randomUUID(), null));

        assertEquals(1, charge.ledgerEntries().size());
        assertEquals(sooner, charge.ledgerEntries().get(0).batchId());
        assertEquals(3, charge.ledgerEntries().get(0).amount());
        assertEquals(2, ledger.remaining.get(sooner));
        assertEquals(5, ledger.remaining.get(later));
    }

    @Test
    void spillsAcrossBatchesWhenFirstIsNotEnough() {
        UUID userId = UUID.randomUUID();
        UUID sooner = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        ledger.batches.add(batch(sooner, NOW.plusSeconds(3_600), 2, 2));
        ledger.batches.add(batch(later, NOW.plusSeconds(86_400), 5, 5));
        entitlements.row.put(userId, writeAccess(userId));

        CreditConsumption.CreditCharge charge = service().consume(
                userId, new CreditConsumeCommand(4, "key-2", CreditLedgerRefType.SCAN, UUID.randomUUID(), null));

        // Một dòng ledger cho MỖI lô bị trừ (p5 R2).
        assertEquals(2, charge.ledgerEntries().size());
        assertEquals(sooner, charge.ledgerEntries().get(0).batchId());
        assertEquals(2, charge.ledgerEntries().get(0).amount());
        assertEquals(later, charge.ledgerEntries().get(1).batchId());
        assertEquals(2, charge.ledgerEntries().get(1).amount());
        assertEquals(0, ledger.remaining.get(sooner));
        assertEquals(3, ledger.remaining.get(later));
    }

    @Test
    void throwsInsufficientCreditWithAvailableAmount() {
        UUID userId = UUID.randomUUID();
        ledger.batches.add(batch(UUID.randomUUID(), NOW.plusSeconds(3_600), 1, 1));
        entitlements.row.put(userId, writeAccess(userId));

        CreditConsumeCommand command =
                new CreditConsumeCommand(5, "key-3", CreditLedgerRefType.SCAN, UUID.randomUUID(), null);
        BusinessRuleException ex = assertThrows(BusinessRuleException.class,
                () -> service().consume(userId, command));
        assertEquals("CREDIT_INSUFFICIENT", ex.errorCode().code());
        // Không ghi dòng ledger nào khi thiếu credit.
        assertTrue(ledger.entries.isEmpty());
    }

    @Test
    void sameIdempotencyKeyDoesNotDeductTwice() {
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        ledger.batches.add(batch(batchId, NOW.plusSeconds(3_600), 10, 10));
        entitlements.row.put(userId, writeAccess(userId));
        UUID scanId = UUID.randomUUID();

        CreditConsumeCommand command =
                new CreditConsumeCommand(2, "key-4", CreditLedgerRefType.SCAN, scanId, null);
        CreditConsumption.CreditCharge first = service().consume(userId, command);
        CreditConsumption.CreditCharge second = service().consume(userId, command);

        assertEquals(first.chargedCredits(), second.chargedCredits());
        assertEquals(first.balanceAfter(), second.balanceAfter());
        // Chỉ có đúng hai dòng ledger của lần trừ ĐẦU (một lô, một dòng).
        assertEquals(1, ledger.entries.size());
        assertEquals(8, ledger.remaining.get(batchId));
    }

    @Test
    void refundReturnsCreditToTheSameBatch() {
        UUID userId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        ledger.batches.add(batch(batchId, NOW.plusSeconds(3_600), 3, 10));
        entitlements.row.put(userId, writeAccess(userId));

        UUID consumeEntryId = UUID.randomUUID();
        ledger.entries.add(new LedgerEntry(consumeEntryId, userId, batchId,
                CreditLedgerType.CONSUME, -2, 3, CreditLedgerRefType.SCAN, UUID.randomUUID(),
                "key-5", null, NOW));

        CreditConsumption.CreditRefund refund = service().refund(userId,
                new CreditRefundCommand(consumeEntryId, "key-5-refund", null));

        assertEquals(batchId, refund.batchId());
        assertEquals(2, refund.refunded());
        assertEquals(5, ledger.remaining.get(batchId));
        // Hoàn bằng dòng REFUND mới, không UPDATE dòng cũ (append-only).
        assertEquals(2, ledger.entries.size());
        assertEquals(CreditLedgerType.REFUND, ledger.entries.getLast().type());
    }

    @Test
    void trialScanDoesNotTouchBatchesOrLedger() {
        UUID userId = UUID.randomUUID();
        ledger.batches.add(batch(UUID.randomUUID(), NOW.plusSeconds(3_600), 5, 5));
        entitlements.row.put(userId, writeAccess(userId));

        assertTrue(service().consumeTrialScan(userId));
        assertEquals(1, entitlements.row.get(userId).trialScansUsed());
        assertTrue(ledger.entries.isEmpty());
        assertTrue(service().hasTrialScanRemaining(userId));
    }

    /** Cổng ledger trong bộ nhớ — đủ các phương thức mà FEFO dùng. */
    private static final class FakeLedgerPort implements CreditLedgerPort {

        final List<CreditBatchSnapshot> batches = new ArrayList<>();
        final Map<UUID, Integer> remaining = new HashMap<>();
        final List<LedgerEntry> entries = new ArrayList<>();

        @Override
        public List<CreditBatchSnapshot> lockLiveBatchesForFefo(UUID userId, Instant now) {
            return batches.stream()
                    .filter(b -> b.expiresAt().isAfter(now) && b.remainingAmount() > 0)
                    .sorted((a, b) -> {
                        int byExpiry = a.expiresAt().compareTo(b.expiresAt());
                        return byExpiry != 0 ? byExpiry : a.id().compareTo(b.id());
                    })
                    .peek(b -> remaining.putIfAbsent(b.id(), b.remainingAmount()))
                    .toList();
        }

        @Override
        public Optional<CreditBatchSnapshot> lockBatchForRefund(UUID batchId) {
            return batches.stream().filter(b -> b.id().equals(batchId)).findFirst();
        }

        @Override
        public void updateRemaining(UUID batchId, int remainingAmount) {
            remaining.put(batchId, remainingAmount);
        }

        @Override
        public void markExhausted(UUID batchId) {
        }

        @Override
        public void markActive(UUID batchId) {
        }

        @Override
        public void markExpired(UUID batchId) {
        }

        @Override
        public void markT48hNotified(UUID batchId) {
        }

        @Override
        public void markT6hNotified(UUID batchId) {
        }

        @Override
        public void append(LedgerEntry entry) {
            entries.add(entry);
        }

        @Override
        public int availableBalance(UUID userId, Instant now) {
            return batches.stream()
                    .filter(b -> b.expiresAt().isAfter(now))
                    .mapToInt(b -> remaining.getOrDefault(b.id(), b.remainingAmount()))
                    .sum();
        }

        @Override
        public int sumLedgerAmountForBatch(UUID batchId) {
            return entries.stream().filter(e -> batchId.equals(e.batchId())).mapToInt(LedgerEntry::amount).sum();
        }

        @Override
        public Optional<LedgerEntryRef> findById(UUID ledgerEntryId) {
            return entries.stream().filter(e -> e.id().equals(ledgerEntryId)).findFirst()
                    .map(e -> new LedgerEntryRef(e.id(), e.type(), e.batchId(), e.userId(), e.amount()));
        }

        @Override
        public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
            return entries.stream().filter(e -> idempotencyKey.equals(e.idempotencyKey())).findFirst();
        }

        @Override
        public List<LedgerEntry> findConsumeRowsByRef(CreditLedgerRefType refType, UUID refId) {
            return entries.stream()
                    .filter(e -> e.type() == CreditLedgerType.CONSUME && refId.equals(e.refId()))
                    .toList();
        }

        @Override
        public boolean existsRefundReferencing(UUID consumedLedgerEntryId) {
            return entries.stream().anyMatch(e -> e.type() == CreditLedgerType.REFUND
                    && consumedLedgerEntryId.equals(e.refId()));
        }
    }

    /** Cổng entitlement trong bộ nhớ. */
    private static final class FakeEntitlementPort implements UserEntitlementPort {

        final Map<UUID, Entitlement> row = new HashMap<>();

        @Override
        public Optional<Entitlement> find(UUID userId) {
            return Optional.ofNullable(row.get(userId));
        }

        @Override
        public Entitlement findOrDefault(UUID userId, Instant now) {
            return row.getOrDefault(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void ensureRow(UUID userId, Instant now) {
            row.putIfAbsent(userId, Entitlement.defaults(userId, now));
        }

        @Override
        public void save(Entitlement entitlement) {
            row.put(entitlement.userId(), entitlement);
        }

        @Override
        public boolean incrementTrialScansUsed(UUID userId, int trialScanLimit, Instant now) {
            Entitlement current = findOrDefault(userId, now);
            if (current.trialScansUsed() >= trialScanLimit) {
                return false;
            }
            save(new Entitlement(current.userId(), current.highestPackage(), current.maxCatProfiles(),
                    current.features(), current.writeAccessUntil(), current.trialScansUsed() + 1, now));
            return true;
        }

        @Override
        public Optional<Instant> maxActivatedBatchExpiry(UUID userId) {
            return Optional.empty();
        }
    }
}
