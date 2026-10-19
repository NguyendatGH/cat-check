package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditBatchSnapshot;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.credit.domain.port.UserEntitlementPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FefoCreditConsumptionService implements com.catcheck.credit.api.CreditConsumption {

    public static final int TRIAL_SCAN_LIMIT = 3;

    private static final Logger log = LoggerFactory.getLogger(FefoCreditConsumptionService.class);

    /** Index partial một-lần-hoàn-mỗi-dòng-trừ (V31). */
    static final String REFUND_REF_UNIQUE_INDEX = "uq_credit_ledger_refund_ref";

    private final CreditLedgerPort creditLedgerPort;
    private final UserEntitlementPort userEntitlementPort;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public FefoCreditConsumptionService(
            CreditLedgerPort creditLedgerPort,
            UserEntitlementPort userEntitlementPort,
            UuidV7 uuidV7,
            Clock clock
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.userEntitlementPort = userEntitlementPort;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public com.catcheck.credit.api.CreditConsumption.CreditCharge consume(
            UUID userId, com.catcheck.credit.api.CreditConsumption.CreditConsumeCommand command) {

        Optional<Replay> replay = findReplay(userId, command.idempotencyKey());
        if (replay.isPresent()) {
            LedgerEntry keyRow = replay.get().keyRow();
            return replay.get().toCharge(creditLedgerPort.findConsumeRowsByRef(
                    toDomain(command.refType()), command.refId()));
        }

        Instant now = clock.instant();
        List<CreditBatchSnapshot> candidates = creditLedgerPort.lockLiveBatchesForFefo(userId, now);
        int available = candidates.stream().mapToInt(CreditBatchSnapshot::remainingAmount).sum();

        if (available < command.credits()) {
            int trialRemaining = trialScansRemaining(userId);
            throw new BusinessRuleException(
                    com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, available, trialRemaining);
        }

        requireWriteAccess(userId, now);

        List<Deduction> deductions = deductFefo(candidates, command.credits());
        int balanceAfter = creditLedgerPort.availableBalance(userId, now);

        List<com.catcheck.credit.api.CreditConsumption.ChargedBatch> charged = new ArrayList<>(deductions.size());
        boolean firstRow = true;
        for (Deduction deduction : deductions) {
            LedgerEntry entry = new LedgerEntry(
                    uuidV7.generate(),
                    userId,
                    deduction.batchId(),
                    com.catcheck.credit.domain.CreditLedgerType.CONSUME,
                    -deduction.amount(),
                    balanceAfter,
                    toDomain(command.refType()),
                    command.refId(),
                    firstRow ? command.idempotencyKey() : null,
                    command.note(),
                    now);
            firstRow = false;
            creditLedgerPort.append(entry);
            charged.add(new com.catcheck.credit.api.CreditConsumption.ChargedBatch(
                    deduction.batchId(), entry.id(), deduction.amount()));
        }

        return new com.catcheck.credit.api.CreditConsumption.CreditCharge(
                command.credits(), balanceAfter, charged);
    }

    private static com.catcheck.credit.domain.CreditLedgerRefType toDomain(
            com.catcheck.credit.api.CreditConsumption.ReferenceType refType) {
        return com.catcheck.credit.domain.CreditLedgerRefType.valueOf(refType.name());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public com.catcheck.credit.api.CreditConsumption.CreditRefund refund(
            UUID userId, com.catcheck.credit.api.CreditConsumption.CreditRefundCommand command) {

        Optional<Replay> replay = findReplay(userId, command.idempotencyKey());
        if (replay.isPresent()) {
            return replay.get().toRefund();
        }

        CreditLedgerPort.LedgerEntryRef original =
                creditLedgerPort.findById(command.consumedLedgerEntryId())
                        .orElseThrow(() -> refundRejected());

        if (!original.userId().equals(userId) || !original.isRefundable()) {
            throw refundRejected();
        }
        Instant now = clock.instant();
        CreditBatchSnapshot target = creditLedgerPort.lockBatchForRefund(original.batchId())
                .orElseThrow(() -> refundRejected());

        // Kiểm tra sau khi giữ khoá lô để hai lần hoàn đồng thời không cùng lọt; V31 là lưới ở DB.
        if (creditLedgerPort.existsRefundReferencing(original.id())) {
            throw refundRejected();
        }

        int refunded = -original.amount();
        if (!target.canRefund(refunded)) {
            throw refundRejected();
        }

        creditLedgerPort.updateRemaining(target.id(), target.remainingAmount() + refunded);
        creditLedgerPort.markActive(target.id());
        int balanceAfter = creditLedgerPort.availableBalance(userId, now);

        LedgerEntry refundEntry = new LedgerEntry(
                uuidV7.generate(),
                userId,
                target.id(),
                com.catcheck.credit.domain.CreditLedgerType.REFUND,
                refunded,
                balanceAfter,
                null,
                command.consumedLedgerEntryId(),
                command.idempotencyKey(),
                command.note(),
                now);
        try {
            creditLedgerPort.append(refundEntry);
        } catch (DuplicateKeyException ex) {
            if (isRefundRefViolation(ex)) {
                throw refundRejected();
            }
            throw ex;
        }

        return new com.catcheck.credit.api.CreditConsumption.CreditRefund(
                target.id(), refundEntry.id(), refunded, balanceAfter);
    }

    private BusinessRuleException refundRejected() {
        return new BusinessRuleException(
                com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, 0, 0);
    }

    /** Chỉ va chạm của {@code uq_credit_ledger_refund_ref} (V31) là "đã hoàn rồi"; va chạm khác giữ nguyên. */
    private static boolean isRefundRefViolation(DuplicateKeyException ex) {
        String message = ex.getMostSpecificCause().getMessage();
        return message != null && message.contains(REFUND_REF_UNIQUE_INDEX);
    }

    @Override
    public boolean hasAvailableCredit(UUID userId) {
        return creditLedgerPort.availableBalance(userId, clock.instant()) > 0;
    }

    @Override
    public boolean hasTrialScanRemaining(UUID userId) {
        return trialScansRemaining(userId) > 0;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY, timeout = 5)
    public boolean consumeTrialScan(UUID userId) {
        Instant now = clock.instant();
        userEntitlementPort.ensureRow(userId, now);
        return userEntitlementPort.incrementTrialScansUsed(userId, TRIAL_SCAN_LIMIT, now);
    }

    private int trialScansRemaining(UUID userId) {
        return Math.max(0, TRIAL_SCAN_LIMIT - userEntitlementPort.findOrDefault(userId, clock.instant()).trialScansUsed());
    }

    private void requireWriteAccess(UUID userId, Instant now) {
        if (!userEntitlementPort.findOrDefault(userId, now).hasWriteAccessAt(now)) {
            throw new PermissionDeniedException(
                    com.catcheck.credit.api.CreditErrorCode.WRITE_ACCESS_EXPIRED);
        }
    }

    private List<Deduction> deductFefo(List<CreditBatchSnapshot> candidates, int requested) {
        List<Deduction> deductions = new ArrayList<>();
        int needed = requested;
        for (CreditBatchSnapshot batch : candidates) {
            if (needed == 0) {
                break;
            }
            int take = Math.min(needed, batch.remainingAmount());
            if (take <= 0) {
                continue;
            }
            int newRemaining = batch.remainingAmount() - take;
            creditLedgerPort.updateRemaining(batch.id(), newRemaining);
            if (newRemaining == 0) {
                creditLedgerPort.markExhausted(batch.id());
            }
            deductions.add(new Deduction(batch.id(), take));
            needed -= take;
        }
        if (needed > 0) {
            log.error("FEFO thiếu {} credit dù đã khoá đủ lô; rollback để giữ bất biến I1", needed);
            throw new BusinessRuleException(
                    com.catcheck.credit.api.CreditErrorCode.CREDIT_INSUFFICIENT, requested - needed, 0);
        }
        return deductions;
    }

    private Optional<Replay> findReplay(UUID userId, String idempotencyKey) {
        Optional<LedgerEntry> existing = creditLedgerPort.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent() && !existing.get().userId().equals(userId)) {
            log.error("Khoá idempotency đã thuộc user khác — lớp idempotency_record (p8 §8.1.8) "
                            + "bị bỏ qua. entryId={} entryUserId={} refType={}",
                    existing.get().id(), existing.get().userId(), existing.get().refType());
            throw new IllegalStateException("idempotency-key thuộc về user khác: " + idempotencyKey);
        }
        return existing.map(Replay::new);
    }

    private record Deduction(UUID batchId, int amount) {
    }

    private record Replay(LedgerEntry keyRow) {

        com.catcheck.credit.api.CreditConsumption.CreditCharge toCharge(
                List<LedgerEntry> chargeRows) {
            int charged = chargeRows.stream().mapToInt(LedgerEntry::amount).sum();
            return new com.catcheck.credit.api.CreditConsumption.CreditCharge(
                    -charged,
                    keyRow.balanceAfter(),
                    chargeRows.stream()
                            .map(row -> new com.catcheck.credit.api.CreditConsumption.ChargedBatch(
                                    row.batchId(), row.id(), -row.amount()))
                            .toList());
        }

        com.catcheck.credit.api.CreditConsumption.CreditRefund toRefund() {
            return new com.catcheck.credit.api.CreditConsumption.CreditRefund(
                    keyRow.batchId(), keyRow.id(), keyRow.amount(), keyRow.balanceAfter());
        }
    }
}
