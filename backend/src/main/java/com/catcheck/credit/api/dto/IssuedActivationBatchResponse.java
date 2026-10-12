package com.catcheck.credit.api.dto;

import java.time.Instant;

public record IssuedActivationBatchResponse(
        String batchId,
        String productionBatch,
        String packageCode,
        int quantity,
        Instant issuedAt,
        Instant validUntil,
        boolean csvAvailable,
        String csvPath
) {
}
