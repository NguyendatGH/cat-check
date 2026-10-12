package com.catcheck.credit.application;

import com.catcheck.credit.domain.CreditLedgerRefType;
import com.catcheck.credit.domain.CreditLedgerType;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.LedgerEntry;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditExpiryService {

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;
    private final UuidV7 uuidV7;

    public CreditExpiryService(
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            UuidV7 uuidV7
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.uuidV7 = uuidV7;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDueBatchIds(Instant now, int limit) {
        return creditBatchPort.findDueForExpiry(now, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int countDueBatches(Instant now) {
        return creditBatchPort.countDueForExpiry(now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean expireBatch(UUID batchId, UUID jobRunId, Instant now) {
        Optional<ExpiringCreditBatch> locked = creditLedgerPort.lockBatchForExpiry(batchId, now);
        if (locked.isEmpty()) {
            return false;
        }

        ExpiringCreditBatch batch = locked.get();
        creditLedgerPort.markExpired(batch.id());

        int balanceAfter = creditLedgerPort.availableBalance(batch.userId(), now);

        creditLedgerPort.append(new LedgerEntry(
                uuidV7.generate(),
                batch.userId(),
                batch.id(),
                CreditLedgerType.EXPIRE,
                -batch.remainingAmount(),
                balanceAfter,
                CreditLedgerRefType.JOB,
                jobRunId,
                null,
                null,
                now));
        return true;
    }
}
