package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

public record RetentionPolicy(
        String code,
        String dataInventoryCode,
        String targetTable,
        Integer retentionDays,
        String anchorColumn,
        RetentionAction actionOnExpiry,
        String jobName,
        int safetyThresholdPercent,
        boolean enabled,
        String legalBasis,
        UUID updatedBy,
        Instant createdAt
) {

    public RetentionPolicy {
        if (code == null || targetTable == null || anchorColumn == null || actionOnExpiry == null) {
            throw new IllegalArgumentException("retentionPolicy thiếu trường bắt buộc");
        }
        if (retentionDays != null && retentionDays <= 0) {
            throw new IllegalArgumentException("retentionPolicy.retentionDays phải > 0 khi NOT NULL (ck_retention_policy_days)");
        }
        if (safetyThresholdPercent < 1 || safetyThresholdPercent > 100) {
            throw new IllegalArgumentException("retentionPolicy.safetyThresholdPercent phải 1..100 (ck_retention_policy_threshold)");
        }
    }
}
