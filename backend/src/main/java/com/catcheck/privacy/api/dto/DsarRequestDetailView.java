package com.catcheck.privacy.api.dto;

import java.time.Instant;

public record DsarRequestDetailView(
        String publicRef,
        String requestType,
        String channel,
        String status,
        String contactEmail,
        Instant identityVerifiedAt,
        String identityMethod,
        Instant receivedAt,
        Instant ackDueAt,
        Instant ackSentAt,
        Instant fulfilDueAt,
        Instant extendedTo,
        String extensionReason,
        boolean thirdPartyInvolved,
        Instant completedAt,
        String rejectionReason,
        Instant resultExpiresAt,
        Instant resultDownloadedAt
) {
}
