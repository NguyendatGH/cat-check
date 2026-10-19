package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record MfaResetRequest(
        UUID id,
        UUID targetUserId,
        UUID requestedBy,
        Instant requestedAt,
        String reason,
        UUID approvedBy,
        Instant approvedAt,
        MfaResetRequestStatus status,
        String rejectReason,
        Instant expiresAt,
        Instant subjectNotifiedAt,
        Instant createdAt,
        Instant updatedAt) {
}
