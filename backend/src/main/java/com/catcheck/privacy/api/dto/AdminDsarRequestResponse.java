package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

/** DSAR admin view: contact email luôn mask, không trả resultRef/storage key. */
public record AdminDsarRequestResponse(
        UUID id,
        String publicRef,
        UUID userId,
        String contactEmailMasked,
        String requestType,
        String channel,
        String status,
        Instant receivedAt,
        Instant ackDueAt,
        Instant ackSentAt,
        Instant fulfilDueAt,
        Instant extendedTo,
        String extensionReason,
        boolean thirdPartyInvolved,
        Instant completedAt,
        String rejectionReason,
        UUID handledBy,
        Instant createdAt) {

    public static AdminDsarRequestResponse from(DsarRequest request) {
        return new AdminDsarRequestResponse(request.id(), request.publicRef(), request.userId(),
                PiiMask.email(request.contactEmail()), request.requestType().name(), request.channel().name(),
                request.status().name(), request.receivedAt(), request.ackDueAt(), request.ackSentAt(),
                request.fulfilDueAt(), request.extendedTo(), request.extensionReason(), request.thirdPartyInvolved(),
                request.completedAt(), request.rejectionReason(), request.handledBy(), request.createdAt());
    }
}
