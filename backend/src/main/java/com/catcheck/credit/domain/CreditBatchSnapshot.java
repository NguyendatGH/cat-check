package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

public record CreditBatchSnapshot(
        UUID id,
        Instant expiresAt,
        int remainingAmount,
        int initialAmount
) {

    public CreditBatchSnapshot {
        if (id == null) {
            throw new IllegalArgumentException("creditBatch.id phải có giá trị");
        }
        if (remainingAmount < 0) {
            throw new IllegalArgumentException("creditBatch.remainingAmount không được âm: " + id);
        }
        if (initialAmount < 0) {
            throw new IllegalArgumentException("creditBatch.initialAmount không được âm: " + id);
        }
        if (remainingAmount > initialAmount) {
            throw new IllegalArgumentException(
                    "creditBatch.remainingAmount vượt initialAmount: " + id);
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("creditBatch.expiresAt phải có giá trị: " + id);
        }
    }

    public boolean canRefund(int amount) {
        return amount > 0 && remainingAmount + amount <= initialAmount;
    }
}
