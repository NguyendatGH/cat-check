package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.RetentionPolicy;

import java.time.Instant;
import java.util.UUID;

/** Response quản trị cho một cấu hình retention; không trả nội dung dữ liệu của user. */
public record RetentionPolicyResponse(
        String code,
        String dataInventoryCode,
        String targetTable,
        Integer retentionDays,
        String anchorColumn,
        String actionOnExpiry,
        String jobName,
        int safetyThresholdPercent,
        boolean enabled,
        String legalBasis,
        UUID updatedBy,
        Instant createdAt
) {

    public static RetentionPolicyResponse from(RetentionPolicy policy) {
        return new RetentionPolicyResponse(
                policy.code(),
                policy.dataInventoryCode(),
                policy.targetTable(),
                policy.retentionDays(),
                policy.anchorColumn(),
                policy.actionOnExpiry().name(),
                policy.jobName(),
                policy.safetyThresholdPercent(),
                policy.enabled(),
                policy.legalBasis(),
                policy.updatedBy(),
                policy.createdAt());
    }
}
