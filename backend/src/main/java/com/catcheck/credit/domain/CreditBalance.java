package com.catcheck.credit.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreditBalance(
        int availableBalance,
        int trialScansUsed,
        int trialScansRemaining,
        List<BatchAvailability> batches
) {

    public CreditBalance {
        if (batches == null) {
            batches = List.of();
        }
    }

    public record BatchAvailability(
            UUID batchId,
            String packageCode,
            int initialAmount,
            int remainingAmount,
            Instant activatedAt,
            Instant expiresAt,
            Duration remaining
    ) {

        public BatchAvailability {
            if (remainingAmount < 0 || remainingAmount > initialAmount) {
                throw new IllegalArgumentException(
                        "remainingAmount phải nằm trong [0, initialAmount]: " + batchId);
            }
        }
    }
}
