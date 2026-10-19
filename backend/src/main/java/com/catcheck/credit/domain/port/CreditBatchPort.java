package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.CreditBatchGrant;
import com.catcheck.credit.domain.CreditBatchView;
import com.catcheck.credit.domain.ExpiryReminderMilestone;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CreditBatchPort {

    void insertNewBatch(CreditBatchGrant batch);

    List<CreditBatchView> findLiveBatches(UUID userId, Instant now);

    List<CreditBatchView> findAllBatches(UUID userId);
    List<UUID> findDueForExpiry(Instant now, int limit);

    int countDueForExpiry(Instant now);

    List<UUID> findDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now, int limit);

    int countDueForExpiryReminder(ExpiryReminderMilestone milestone, Instant now);
}
