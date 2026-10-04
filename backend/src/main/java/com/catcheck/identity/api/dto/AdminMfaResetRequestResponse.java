package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.MfaResetRequest;

import java.time.Instant;
import java.util.UUID;

public record AdminMfaResetRequestResponse(
        UUID id,
        UUID targetUserId,
        UUID requestedBy,
        Instant requestedAt,
        String reason,
        String status,
        Instant expiresAt,
        UUID approvedBy,
        Instant approvedAt) {

    public static AdminMfaResetRequestResponse from(MfaResetRequest request) {
        return new AdminMfaResetRequestResponse(request.id(), request.targetUserId(), request.requestedBy(),
                request.requestedAt(), request.reason(), request.status().name(), request.expiresAt(),
                request.approvedBy(), request.approvedAt());
    }
}
