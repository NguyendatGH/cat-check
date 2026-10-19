package com.catcheck.credit.api;

import java.util.List;
import java.util.UUID;

public interface CreditConsumption {

    CreditCharge consume(UUID userId, CreditConsumeCommand command);

    CreditRefund refund(UUID userId, CreditRefundCommand command);

    boolean hasAvailableCredit(UUID userId);

    boolean hasTrialScanRemaining(UUID userId);

    boolean consumeTrialScan(UUID userId);

    record CreditConsumeCommand(
            int credits,
            String idempotencyKey,
            ReferenceType refType,
            UUID refId,
            String note
    ) {

        public CreditConsumeCommand {
            if (credits < 1) {
                throw new IllegalArgumentException("credits phải >= 1: " + credits);
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey là bắt buộc (p5 R8)");
            }
        }
    }

    enum ReferenceType {
        SCAN,
        ACTIVATION,
        JOB,
        ADMIN
    }

    record CreditRefundCommand(
            UUID consumedLedgerEntryId,
            String idempotencyKey,
            String note
    ) {

        public CreditRefundCommand {
            if (consumedLedgerEntryId == null) {
                throw new IllegalArgumentException("consumedLedgerEntryId là bắt buộc");
            }
            if (idempotencyKey == null || idempotencyKey.isBlank()) {
                throw new IllegalArgumentException("idempotencyKey là bắt buộc (p5 R8)");
            }
        }
    }

    record CreditCharge(
            int chargedCredits,
            int balanceAfter,
            List<ChargedBatch> ledgerEntries
    ) {

        public CreditCharge {
            ledgerEntries = ledgerEntries == null ? List.of() : List.copyOf(ledgerEntries);
        }
    }

    record ChargedBatch(UUID batchId, UUID ledgerId, int amount) {

        public ChargedBatch {
            if (batchId == null || ledgerId == null || amount < 1) {
                throw new IllegalArgumentException("chargedBatch không hợp lệ");
            }
        }
    }

    record CreditRefund(
            UUID batchId,
            UUID refundId,
            int refunded,
            int balanceAfter
    ) {
    }
}
