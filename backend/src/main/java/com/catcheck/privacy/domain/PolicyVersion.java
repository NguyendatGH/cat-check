package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyVersion(
        UUID id,
        PolicyType policyType,
        String version,
        String locale,
        String title,
        String contentMd,
        String contentUrl,
        String contentHash,
        String summaryOfChanges,
        boolean requiresReconsent,
        List<String> affectedPurposes,
        Instant effectiveFrom,
        Instant effectiveTo,
        UUID publishedBy,
        Instant createdAt
) {

    public PolicyVersion {
        if (id == null || policyType == null || version == null || locale == null) {
            throw new IllegalArgumentException("policyVersion thiếu trường bắt buộc");
        }
        if (contentMd == null && contentUrl == null) {
            throw new IllegalArgumentException(
                    "policyVersion phải có content_md hoặc content_url (ck_policy_version_source)");
        }
        if (contentHash == null || contentHash.length() != 64) {
            throw new IllegalArgumentException("policyVersion.contentHash phải là SHA-256 (64 ký tự hex)");
        }
        if (effectiveFrom == null) {
            throw new IllegalArgumentException("policyVersion.effectiveFrom bắt buộc");
        }
        affectedPurposes = affectedPurposes == null ? List.of() : List.copyOf(affectedPurposes);
    }

    public boolean isEffectiveAt(Instant now) {
        return !effectiveFrom.isAfter(now)
                && (effectiveTo == null || effectiveTo.isAfter(now));
    }
}
