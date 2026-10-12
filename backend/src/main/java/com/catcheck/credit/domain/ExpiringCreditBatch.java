package com.catcheck.credit.domain;

import java.time.Instant;
import java.util.UUID;

public record ExpiringCreditBatch(
        UUID id,
        UUID userId,
        Instant expiresAt,
        int remainingAmount,
        int initialAmount
) {

    public ExpiringCreditBatch {
        if (id == null || userId == null || expiresAt == null) {
            throw new IllegalArgumentException("expiringCreditBatch thiếu trường bắt buộc");
        }
        if (remainingAmount <= 0) {
            throw new IllegalArgumentException(
                    "expiringCreditBatch.remainingAmount phải > 0: " + id);
        }
        if (remainingAmount > initialAmount) {
            throw new IllegalArgumentException(
                    "expiringCreditBatch.remainingAmount vượt initialAmount: " + id);
        }
    }
}
