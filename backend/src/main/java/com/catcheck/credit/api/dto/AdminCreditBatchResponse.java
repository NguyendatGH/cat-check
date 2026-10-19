package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.CreditBatchView;

import java.time.Instant;

public record AdminCreditBatchResponse(
        String batchId,
        String packageCode,
        Integer packageVersion,
        int initialAmount,
        int remainingAmount,
        String status,
        Instant activatedAt,
        Instant expiresAt,
        Boolean adminGranted
) {

    public static AdminCreditBatchResponse from(CreditBatchView batch) {
        return new AdminCreditBatchResponse(
                batch.id().toString(),
                batch.packageCode(),
                null,
                batch.initialAmount(),
                batch.remainingAmount(),
                batch.status() == null ? null : batch.status().name(),
                batch.activatedAt(),
                batch.expiresAt(),
                null);
    }
}
