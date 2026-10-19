package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.LedgerEntry;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CreditLedgerPort {

    List<CreditBatchSnapshot> lockLiveBatchesForFefo(UUID userId, Instant now);

    Optional<CreditBatchSnapshot> lockBatchForRefund(UUID batchId);

    void updateRemaining(UUID batchId, int remainingAmount);

    void markExhausted(UUID batchId);

    void markActive(UUID batchId);

    void markExpired(UUID batchId);

    void markT48hNotified(UUID batchId);

    void markT6hNotified(UUID batchId);

    Optional<ExpiringCreditBatch> lockBatchForExpiry(UUID batchId, Instant now);

    Optional<ExpiringCreditBatch> lockBatchForExpiryReminder(
            UUID batchId, ExpiryReminderMilestone milestone, Instant now);

    void append(LedgerEntry entry);

    int availableBalance(UUID userId, Instant now);

    int sumLedgerAmountForBatch(UUID batchId);

    Optional<LedgerEntryRef> findById(UUID ledgerEntryId);

    Optional<LedgerEntry> findByIdempotencyKey(String idempotencyKey);

    List<LedgerEntry> findConsumeRowsByRef(CreditLedgerRefType refType, UUID refId);

    int sumAdminAdjustedAbsSince(UUID adminId, Instant since);

    boolean existsRefundReferencing(UUID consumedLedgerEntryId);

    record LedgerEntryRef(UUID id, CreditLedgerType type, UUID batchId, UUID userId, int amount) {

        public LedgerEntryRef {
            if (id == null || batchId == null || userId == null) {
                throw new IllegalArgumentException("ledgerEntryRef thiếu trường bắt buộc");
            }
            if (type == null) {
                throw new IllegalArgumentException("ledgerEntryRef thiếu type");
            }
            if (amount >= 0) {
                throw new IllegalArgumentException("Chỉ hoàn được dòng CONSUME/EXPIRE (amount âm): " + amount);
            }
        }

        public boolean isRefundable() {
            return type == CreditLedgerType.CONSUME || type == CreditLedgerType.EXPIRE;
        }
    }
}
