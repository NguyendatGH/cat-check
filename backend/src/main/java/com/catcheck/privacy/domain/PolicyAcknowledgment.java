package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

public record PolicyAcknowledgment(
        UUID id,
        UUID userId,
        UUID policyVersionId,
        String policyHash,
        PolicySurface surface,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {

    public PolicyAcknowledgment {
        if (id == null || userId == null || policyVersionId == null || policyHash == null) {
            throw new IllegalArgumentException("policyAcknowledgment thiếu trường bắt buộc");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("policyAcknowledgment.occurredAt bắt buộc");
        }
    }
}
