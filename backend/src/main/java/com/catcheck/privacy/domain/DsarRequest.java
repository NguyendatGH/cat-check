package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

public record DsarRequest(
        UUID id,
        String publicRef,
        UUID userId,
        String contactEmail,
        DsarRequestType requestType,
        DsarChannel channel,
        DsarStatus status,
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
        String resultRef,
        Instant resultExpiresAt,
        Instant resultDownloadedAt,
        UUID handledBy,
        UUID pseudonymId,
        Instant createdAt
) {

    public DsarRequest {
        if (id == null || publicRef == null || contactEmail == null) {
            throw new IllegalArgumentException("dsarRequest thiếu trường bắt buộc");
        }
        if (requestType == null || channel == null || status == null) {
            throw new IllegalArgumentException("dsarRequest bắt buộc có requestType, channel, status");
        }
        if (receivedAt == null || ackDueAt == null || fulfilDueAt == null) {
            throw new IllegalArgumentException("dsarRequest bắt buộc tính ackDueAt và fulfilDueAt lúc tạo (p4 B5)");
        }
        if (!ackDueAt.isAfter(receivedAt) || !fulfilDueAt.isAfter(receivedAt)) {
            throw new IllegalArgumentException("dsarRequest ackDueAt/fulfilDueAt phải sau receivedAt (ck_dsar_request_*)");
        }
        if (extendedTo != null && extensionReason == null) {
            throw new IllegalArgumentException("dsarRequest gia hạn bắt buộc có extensionReason (ck_dsar_request_extension_reason)");
        }
        if (extendedTo != null && !extendedTo.isAfter(fulfilDueAt)) {
            throw new IllegalArgumentException("dsarRequest.extendedTo phải sau fulfilDueAt (ck_dsar_request_extended)");
        }
        if (status == DsarStatus.REJECTED && rejectionReason == null) {
            throw new IllegalArgumentException("dsarRequest REJECTED bắt buộc có rejectionReason (Đ13.3/Đ14.5)");
        }
    }

    public static int fulfilDaysFor(DsarRequestType type) {
        return switch (type) {
            case ACCESS_EXPORT, RECTIFY -> 10;
            case RESTRICT, OBJECT, WITHDRAW_CONSENT, PROTECTION_MEASURE, COMPLAINT -> 15;
            case ERASE -> 20;
        };
    }

    public boolean requiresIdentityVerification() {
        return requestType == DsarRequestType.ACCESS_EXPORT || requestType == DsarRequestType.ERASE;
    }

    public boolean isDownloadable(Instant now) {
        return status == DsarStatus.COMPLETED && resultRef != null && resultDownloadedAt == null
                && resultExpiresAt != null && resultExpiresAt.isAfter(now);
    }

    public DsarRequest withStatus(DsarStatus newStatus) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, newStatus,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withResultDownloadedAt(Instant downloadedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, downloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withAckSentAt(Instant sentAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, sentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withHandledBy(UUID handlerId) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handlerId, pseudonymId, createdAt);
    }

    public DsarRequest withExtension(Instant newDueAt, String reason) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                newDueAt, reason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withRejectionReason(String reason) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, reason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withCompletedAt(Instant completedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel, status,
                identityVerifiedAt, identityMethod, receivedAt, ackDueAt, ackSentAt, fulfilDueAt,
                extendedTo, extensionReason, thirdPartyInvolved, completedAt, rejectionReason,
                resultRef, resultExpiresAt, resultDownloadedAt, handledBy, pseudonymId, createdAt);
    }

    public DsarRequest withExportResult(String storageRef, Instant expiresAt, Instant completedAt) {
        return new DsarRequest(id, publicRef, userId, contactEmail, requestType, channel,
                DsarStatus.COMPLETED, identityVerifiedAt, identityMethod, receivedAt, ackDueAt,
                ackSentAt, fulfilDueAt, extendedTo, extensionReason, thirdPartyInvolved,
                completedAt, rejectionReason, storageRef, expiresAt, null, handledBy,
                pseudonymId, createdAt);
    }
}
