package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.RetentionService;

import java.time.Instant;

/** Kết quả L58 — số liệu ước tính, không phải lệnh xoá. */
public record AdminRetentionDryRunResponse(
        String policyCode,
        String targetTable,
        String anchorColumn,
        Integer retentionDays,
        Instant cutoff,
        long candidateCount,
        String actionOnExpiry) {

    public static AdminRetentionDryRunResponse from(RetentionService.DryRunResult result) {
        return new AdminRetentionDryRunResponse(result.policyCode(), result.targetTable(), result.anchorColumn(),
                result.retentionDays(), result.cutoff(), result.candidateCount(), result.actionOnExpiry());
    }
}
