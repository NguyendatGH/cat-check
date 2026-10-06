package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditBatchStatus;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code credit_batch} + {@code credit_ledger} trong bộ nhớ, dùng cho hai job nhóm A và cho
 * {@code AdminCreditServiceTest} (L9/L10 — W5-A).
 *
 * <p>Fake này <b>lặp lại đúng các bộ lọc của câu SQL thật</b> ({@code status = 'ACTIVE'},
 * {@code expires_at <= now}, {@code remaining_amount > 0}, cột cờ đã nhắc còn NULL) — đó mới là
 * thứ quyết định tính idempotent mà p12 §12.6.2 đòi, nên nếu fake lọc lỏng hơn thì test sẽ xanh
 * trong khi job thật ghi ledger trùng. {@link #locked} mô phỏng {@code SKIP LOCKED}: lô trong
 * tập này coi như đang bị transaction khác giữ và không khoá được.</p>
 */
final class InMemoryCreditStore implements CreditBatchPort, CreditLedgerPort {

    /** Một dòng {@code credit_batch} có thể thay đổi. */
    static final class Batch {
        final UUID id;
        final UUID userId;
        final Instant expiresAt;
        final int initialAmount;
        int remainingAmount;
        CreditBatchStatus status = CreditBatchStatus.ACTIVE;
        Instant t48hNotifiedAt;
        Instant t6hNotifiedAt;
        String packageCode = "PLUS";
        Instant activatedAt = Instant.EPOCH;

        Batch(UUID id, UUID userId, Instant expiresAt, int remainingAmount, int initialAmount) {
            this.id = id;
            this.userId = userId;
            this.expiresAt = expiresAt;
            this.remainingAmount = remainingAmount;
            this.initialAmount = initialAmount;
        }
    }

    final List<Batch> batches = new ArrayList<>();
    final List<LedgerEntry> entries = new ArrayList<>();

    /** Lô "đang bị khoá bởi transaction khác" — {@code FOR UPDATE SKIP LOCKED} bỏ qua chúng. */
    final Set<UUID> locked = new HashSet<>();

    /** Lô mà {@link #markExpired} sẽ ném — mô phỏng lỗi một lô không chặn lô khác. */
    final Set<UUID> failing = new HashSet<>();

    Batch addBatch(UUID userId, Instant expiresAt, int remaining, int initial) {
        Batch batch = new Batch(UUID.randomUUID(), userId, expiresAt, remaining, initial);
        batches.add(batch);
        return batch;
    }

    Batch byId(UUID id) {
        return batches.stream().filter(b -> b.id.equals(id)).findFirst().orElseThrow();
    }

    List<LedgerEntry> entriesOfType(CreditLedgerType type) {
        return entries.stream().filter(e -> e.type() == type).toList();
    }

    // ------------------------------------------------------------------ CreditBatchPort

    @Override
    public List<UUID> findDueForExpiry(Instant now, int limit) {
        return batches.stream()
                .filter(b -> isDueForExpiry(b, now))
                .sorted(Comparator.comparing((Batch b) -> b.expiresAt).thenComparing(b -> b.id))
                .limit(limit)
                .map(b -> b.id)
                .toList();
    }

    @Override
    public int countDueForExpiry(Instant now) {
        return (int) batches.stream().filter(b -> isDueForExpiry(b, now)).count();
    }

    @Override
    public List<UUID> findDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now, int limit) {
        return batches.stream()
                .filter(b -> isDueForReminder(b, milestone, now))
                .sorted(Comparator.comparing((Batch b) -> b.expiresAt).thenComparing(b -> b.id))
                .limit(limit)
                .map(b -> b.id)
                .toList();
    }

    @Override
    public int countDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now) {
        return (int) batches.stream().filter(b -> isDueForReminder(b, milestone, now)).count();
    }

    @Override
    public void insertNewBatch(CreditBatchGrant batch) {
        Batch row = new Batch(batch.id(), batch.userId(), batch.expiresAt(),
                batch.creditAmount(), batch.creditAmount());
        row.packageCode = batch.packageCode();
        row.activatedAt = batch.activatedAt();
        batches.add(row);
    }

    @Override
    public List<CreditBatchView> findLiveBatches(UUID userId, Instant now) {
        return batches.stream()
                .filter(b -> b.userId.equals(userId)
                        && b.status == CreditBatchStatus.ACTIVE
                        && b.expiresAt.isAfter(now)
                        && b.remainingAmount > 0)
                .sorted(Comparator.comparing((Batch b) -> b.expiresAt).thenComparing(b -> b.id))
                .map(InMemoryCreditStore::toView)
                .toList();
    }

    @Override
    public List<CreditBatchView> findAllBatches(UUID userId) {
        return batches.stream()
                .filter(b -> b.userId.equals(userId))
                .sorted(Comparator.comparing((Batch b) -> b.expiresAt).thenComparing(b -> b.id))
                .map(InMemoryCreditStore::toView)
                .toList();
    }

    // ----------------------------------------------------------------- CreditLedgerPort

    @Override
    public Optional<ExpiringCreditBatch> lockBatchForExpiry(UUID batchId, Instant now) {
        return batches.stream()
                .filter(b -> b.id.equals(batchId) && isDueForExpiry(b, now) && !locked.contains(b.id))
                .findFirst()
                .map(InMemoryCreditStore::toExpiring);
    }

    @Override
    public Optional<ExpiringCreditBatch> lockBatchForExpiryReminder(
            UUID batchId, ExpiryReminderMilestone milestone, Instant now) {
        return batches.stream()
                .filter(b -> b.id.equals(batchId) && isDueForReminder(b, milestone, now)
                        && !locked.contains(b.id))
                .findFirst()
                .map(InMemoryCreditStore::toExpiring);
    }

    @Override
    public void markExpired(UUID batchId) {
        if (failing.contains(batchId)) {
            throw new IllegalStateException("loi gia lap khi dong lo " + batchId);
        }
        Batch batch = byId(batchId);
        if (batch.status == CreditBatchStatus.ACTIVE) {
            batch.status = CreditBatchStatus.EXPIRED;
            batch.remainingAmount = 0;
        }
    }

    @Override
    public void markT48hNotified(UUID batchId) {
        byId(batchId).t48hNotifiedAt = Instant.EPOCH;
    }

    @Override
    public void markT6hNotified(UUID batchId) {
        byId(batchId).t6hNotifiedAt = Instant.EPOCH;
    }

    @Override
    public void append(LedgerEntry entry) {
        entries.add(entry);
    }

    @Override
    public int availableBalance(UUID userId, Instant now) {
        return batches.stream()
                .filter(b -> b.userId.equals(userId)
                        && b.status == CreditBatchStatus.ACTIVE
                        && b.expiresAt.isAfter(now))
                .mapToInt(b -> b.remainingAmount)
                .sum();
    }

    /**
     * Lặp lại đúng bộ lọc + <b>thứ tự</b> của câu SQL thật ({@code status='ACTIVE'},
     * {@code expires_at > now}, {@code remaining_amount > 0}, {@code ORDER BY expires_at, id}).
     * Thứ tự là phần quan trọng nhất: nó mới là FEFO (p5 R2) — fake sắp sai thì test xanh trong
     * khi code thật trừ sai lô.
     */
    @Override
    public List<CreditBatchSnapshot> lockLiveBatchesForFefo(UUID userId, Instant now) {
        return batches.stream()
                .filter(b -> b.userId.equals(userId)
                        && b.status == CreditBatchStatus.ACTIVE
                        && b.expiresAt.isAfter(now)
                        && b.remainingAmount > 0
                        && !locked.contains(b.id))
                .sorted(Comparator.comparing((Batch b) -> b.expiresAt).thenComparing(b -> b.id))
                .map(b -> new CreditBatchSnapshot(b.id, b.expiresAt, b.remainingAmount, b.initialAmount))
                .toList();
    }

    @Override
    public Optional<CreditBatchSnapshot> lockBatchForRefund(UUID batchId) {
        throw new UnsupportedOperationException("khong dung trong test job");
    }

    @Override
    public void updateRemaining(UUID batchId, int remainingAmount) {
        byId(batchId).remainingAmount = remainingAmount;
    }

    @Override
    public void markExhausted(UUID batchId) {
        byId(batchId).status = CreditBatchStatus.EXHAUSTED;
    }

    @Override
    public void markActive(UUID batchId) {
        byId(batchId).status = CreditBatchStatus.ACTIVE;
    }

    @Override
    public int sumLedgerAmountForBatch(UUID batchId) {
        return entries.stream().filter(e -> batchId.equals(e.batchId())).mapToInt(LedgerEntry::amount).sum();
    }

    @Override
    public Optional<LedgerEntryRef> findById(UUID ledgerEntryId) {
        return Optional.empty();
    }

    @Override
    public Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey) {
        return entries.stream()
                .filter(entry -> idempotencyKey != null && idempotencyKey.equals(entry.idempotencyKey()))
                .findFirst();
    }

    @Override
    public List<LedgerEntry> findConsumeRowsByRef(CreditLedgerRefType refType, UUID refId) {
        return List.of();
    }

    /** Trần {@code maxPerDay} của L10 — tính đúng từ các dòng đã ghi, không trả 0 cứng. */
    @Override
    public int sumAdminAdjustedAbsSince(UUID adminId, Instant since) {
        return entries.stream()
                .filter(entry -> entry.refType() == CreditLedgerRefType.ADMIN)
                .filter(entry -> adminId.equals(entry.refId()))
                .filter(entry -> !entry.createdAt().isBefore(since))
                .mapToInt(entry -> Math.abs(entry.amount()))
                .sum();
    }

    @Override
    public boolean existsRefundReferencing(UUID consumedLedgerEntryId) {
        return false;
    }

    // ------------------------------------------------------------------------- bộ lọc

    private static boolean isDueForExpiry(Batch batch, Instant now) {
        return batch.status == CreditBatchStatus.ACTIVE
                && !batch.expiresAt.isAfter(now)
                && batch.remainingAmount > 0;
    }

    private static boolean isDueForReminder(Batch batch, ExpiryReminderMilestone milestone, Instant now) {
        boolean alreadyNotified = switch (milestone) {
            case T48H -> batch.t48hNotifiedAt != null;
            case T6H -> batch.t6hNotifiedAt != null;
        };
        return batch.status == CreditBatchStatus.ACTIVE
                && batch.remainingAmount > 0
                && batch.expiresAt.isAfter(now)
                && !batch.expiresAt.isAfter(now.plus(milestone.lead()))
                && !alreadyNotified;
    }

    private static CreditBatchView toView(Batch batch) {
        return new CreditBatchView(batch.id, batch.packageCode, batch.initialAmount,
                batch.remainingAmount, batch.activatedAt, batch.expiresAt, batch.status);
    }

    private static ExpiringCreditBatch toExpiring(Batch batch) {
        return new ExpiringCreditBatch(
                batch.id, batch.userId, batch.expiresAt, batch.remainingAmount, batch.initialAmount);
    }
}
