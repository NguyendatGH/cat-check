package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.PolicyVersionAdminService;

import java.time.Instant;
import java.util.List;

public record AdminPolicyVersionResponse(
        String id,
        String policyType,
        String version,
        String locale,
        String title,
        Boolean hasContent,
        String contentUrl,
        String contentHash,
        String summaryOfChanges,
        Boolean requiresReconsent,
        List<String> affectedPurposes,
        Instant effectiveFrom,
        Instant effectiveTo,
        String publishedBy,
        Instant createdAt,
        String status
) {

    public static AdminPolicyVersionResponse from(PolicyVersionAdminService.AdminPolicyRow row) {
        var v = row.version();
        return new AdminPolicyVersionResponse(
                v.id().toString(),
                v.policyType().name(),
                v.version(),
                v.locale(),
                v.title(),
                row.hasContent(),
                v.contentUrl(),
                v.contentHash(),
                v.summaryOfChanges(),
                v.requiresReconsent(),
                v.affectedPurposes(),
                v.effectiveFrom(),
                v.effectiveTo(),
                v.publishedBy() == null ? null : v.publishedBy().toString(),
                v.createdAt(),
                row.status().name());
    }
}
