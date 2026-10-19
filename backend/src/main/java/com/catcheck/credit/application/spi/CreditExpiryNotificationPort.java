package com.catcheck.credit.application.spi;

import com.catcheck.credit.domain.ExpiryReminderMilestone;

import java.time.Instant;
import java.util.UUID;

public interface CreditExpiryNotificationPort {

    void queueExpiryReminder(
            UUID userId,
            UUID batchId,
            ExpiryReminderMilestone milestone,
            Instant expiresAt,
            int remainingAmount);
}
