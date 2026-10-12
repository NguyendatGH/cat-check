package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ConsentRecord(
        UUID id,
        UUID userId,
        String purposeCode,
        ConsentStatus status,
        UUID policyVersionId,
        String policyHash,
        String consentTextHash,
        ConsentMethod method,
        String uiSurface,
        String locale,
        UUID supersedesId,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> evidence,
        Instant createdAt
) {

    public ConsentRecord {
        if (id == null || userId == null || purposeCode == null || status == null) {
            throw new IllegalArgumentException("consentRecord thiếu trường bắt buộc");
        }
        if (policyVersionId == null || policyHash == null || consentTextHash == null || method == null) {
            throw new IllegalArgumentException(
                    "consentRecord bắt buộc có policyVersionId, policyHash, consentTextHash, method (p15 §15.10-C)");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("consentRecord.occurredAt bắt buộc");
        }
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
