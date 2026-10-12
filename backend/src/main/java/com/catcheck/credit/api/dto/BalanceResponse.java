package com.catcheck.credit.api.dto;

import com.catcheck.credit.domain.CreditBalance;

import java.time.Instant;
import java.util.List;

public record BalanceResponse(
        int availableBalance,
        int trialScansUsed,
        int trialScansRemaining,
        List<BatchResponse> batches
) {

    public static BalanceResponse from(CreditBalance balance) {
        return new BalanceResponse(
                balance.availableBalance(),
                balance.trialScansUsed(),
                balance.trialScansRemaining(),
                balance.batches().stream().map(BatchResponse::from).toList());
    }

    public record BatchResponse(
            String batchId,
            String packageCode,
            int initialAmount,
            int remainingAmount,
            Instant activatedAt,
            Instant expiresAt,
            long remainingSeconds
    ) {

        static BatchResponse from(CreditBalance.BatchAvailability batch) {
            return new BatchResponse(
                    batch.batchId().toString(),
                    batch.packageCode(),
                    batch.initialAmount(),
                    batch.remainingAmount(),
                    batch.activatedAt(),
                    batch.expiresAt(),
                    batch.remaining() == null ? 0L : batch.remaining().toSeconds());
        }
    }
}
