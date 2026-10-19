package com.catcheck.credit.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record CreditBatchGrant(
        UUID id,
        UUID userId,
        UUID activationCodeId,
        String packageCode,
        int packageVersion,
        int creditAmount,
        Instant activatedAt,
        Instant expiresAt
) {

    public CreditBatchGrant {
        if (id == null || userId == null || packageCode == null || activatedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("creditBatchGrant thiếu trường bắt buộc");
        }
        if (creditAmount <= 0) {
            throw new IllegalArgumentException("creditBatchGrant.creditAmount phải > 0: " + id);
        }
        if (!expiresAt.isAfter(activatedAt)) {
            throw new IllegalArgumentException(
                    "creditBatchGrant cần expiresAt > activatedAt (ck_credit_batch_expiry): " + id);
        }
    }

    public static CreditBatchGrant of(
            UUID id, UUID userId, UUID activationCodeId, PackagePlan plan, Instant activatedAt) {
        return new CreditBatchGrant(
                id,
                userId,
                activationCodeId,
                plan.code(),
                plan.version(),
                plan.creditAmount(),
                activatedAt,
                activatedAt.plus(Duration.ofDays(plan.creditValidityDays())));
    }

    public CreditBatchSnapshot toSnapshot() {
        return new CreditBatchSnapshot(id, expiresAt, creditAmount, creditAmount);
    }
}
