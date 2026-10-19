package com.catcheck.credit.domain;

import java.time.Instant;

public record ActivationBatchSummary(
        String productionBatch,
        String packageCode,
        long totalCodes,
        long issuedCodes,
        long redeemedCodes,
        long voidedCodes,
        Instant issuedAt,
        Instant validUntil
) {

    public ActivationBatchSummary {
        if (productionBatch == null || productionBatch.isBlank()) {
            throw new IllegalArgumentException("activationBatch.productionBatch phải có giá trị");
        }
    }
}
