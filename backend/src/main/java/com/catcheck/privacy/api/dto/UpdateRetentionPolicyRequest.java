package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.RetentionAction;

public record UpdateRetentionPolicyRequest(
        String dataInventoryCode,
        String targetTable,
        Integer retentionDays,
        String anchorColumn,
        RetentionAction actionOnExpiry,
        String jobName,
        Integer safetyThresholdPercent,
        Boolean enabled,
        String legalBasis
) {
}
