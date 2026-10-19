package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.ConsentRecord;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AdminConsentRecordView(
        UUID id,
        UUID userId,
        String purposeCode,
        String status,
        UUID policyVersionId,
        String policyHash,
        String consentTextHash,
        String method,
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

    public static AdminConsentRecordView from(ConsentRecord record) {
        return new AdminConsentRecordView(
                record.id(), record.userId(), record.purposeCode(), record.status().name(),
                record.policyVersionId(), record.policyHash(), record.consentTextHash(),
                record.method().name(), record.uiSurface(), record.locale(), record.supersedesId(),
                record.occurredAt(), record.ipAddress(), record.userAgent(), record.requestId(),
                record.evidence(), record.createdAt());
    }
}
