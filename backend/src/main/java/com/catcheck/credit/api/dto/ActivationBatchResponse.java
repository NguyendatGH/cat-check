package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.ActivationBatchSummary;

import java.time.Instant;

public record ActivationBatchResponse(
        String batchId,
        String productionBatch,
        String packageCode,
        long totalCodes,
        long issuedCodes,
        long redeemedCodes,
        long voidedCodes,
        Instant issuedAt,
        Instant validUntil,
        boolean csvAvailable
) {

    public static ActivationBatchResponse from(ActivationBatchSummary batch, boolean csvAvailable) {
        return new ActivationBatchResponse(
                batch.productionBatch(),
                batch.productionBatch(),
                batch.packageCode(),
                batch.totalCodes(),
                batch.issuedCodes(),
                batch.redeemedCodes(),
                batch.voidedCodes(),
                batch.issuedAt(),
                batch.validUntil(),
                csvAvailable);
    }
}
