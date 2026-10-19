package com.catcheck.credit.api.dto;

import com.catcheck.credit.application.AdminCreditService.AdminCreditAdjustmentResult;

import java.time.Instant;
import java.util.List;

public record CreditAdjustmentResponse(
        String direction,
        int amount,
        int balanceBefore,
        int balanceAfter,
        String batchId,
        Instant expiresAt,
        List<BatchDelta> batches,
        boolean replayed
) {

    public static CreditAdjustmentResponse from(AdminCreditAdjustmentResult result) {
        return new CreditAdjustmentResponse(
                result.direction().name(),
                result.amount(),
                result.balanceBefore(),
                result.balanceAfter(),
                result.batchId() == null ? null : result.batchId().toString(),
                result.expiresAt(),
                result.batches().stream()
                        .map(delta -> new BatchDelta(
                                delta.batchId() == null ? null : delta.batchId().toString(),
                                delta.amount()))
                        .toList(),
                result.replayed());
    }

    public record BatchDelta(String batchId, int amount) {
    }
}
