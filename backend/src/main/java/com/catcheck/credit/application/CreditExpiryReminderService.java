package com.catcheck.credit.application;

import com.catcheck.credit.application.spi.CreditExpiryNotificationPort;
import com.catcheck.credit.domain.ExpiringCreditBatch;
import com.catcheck.credit.domain.ExpiryReminderMilestone;
import com.catcheck.credit.domain.port.CreditBatchPort;
import com.catcheck.credit.domain.port.CreditLedgerPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CreditExpiryReminderService {

    private final CreditLedgerPort creditLedgerPort;
    private final CreditBatchPort creditBatchPort;

    private final ObjectProvider<CreditExpiryNotificationPort> notificationPort;

    public CreditExpiryReminderService(
            CreditLedgerPort creditLedgerPort,
            CreditBatchPort creditBatchPort,
            ObjectProvider<CreditExpiryNotificationPort> notificationPort
    ) {
        this.creditLedgerPort = creditLedgerPort;
        this.creditBatchPort = creditBatchPort;
        this.notificationPort = notificationPort;
    }

    public boolean notificationAvailable() {
        return notificationPort.getIfAvailable() != null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<UUID> findDueBatchIds(ExpiryReminderMilestone milestone, Instant now, int limit) {
        return creditBatchPort.findDueForExpiryReminder(milestone, now, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public int countDueBatches(ExpiryReminderMilestone milestone, Instant now) {
        return creditBatchPort.countDueForExpiryReminder(milestone, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 10)
    public boolean queueReminder(UUID batchId, ExpiryReminderMilestone milestone, Instant now) {
        Optional<ExpiringCreditBatch> locked =
                creditLedgerPort.lockBatchForExpiryReminder(batchId, milestone, now);
        if (locked.isEmpty()) {
            return false;
        }

        CreditExpiryNotificationPort outbox = notificationPort.getIfAvailable();
        if (outbox == null) {
            throw new IllegalStateException(
                    "Thieu adapter CreditExpiryNotificationPort — module notification chua co outbox port (H15.e)");
        }

        ExpiringCreditBatch batch = locked.get();
        outbox.queueExpiryReminder(
                batch.userId(), batch.id(), milestone, batch.expiresAt(), batch.remainingAmount());

        switch (milestone) {
            case T48H -> creditLedgerPort.markT48hNotified(batch.id());
            case T6H -> creditLedgerPort.markT6hNotified(batch.id());
        }
        return true;
    }
}
