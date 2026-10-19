package com.catcheck.identity.api.dto;

import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummaryResponse(
        UUID userId,
        String emailMasked,
        String fullName,
        String status,
        String onboardingStatus,
        boolean emailVerified,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        long catCount,
        long scanCount,
        String highestPackage
) {

    public static AdminUserSummaryResponse from(AdminUserSummary user) {
        return new AdminUserSummaryResponse(
                user.userId(),
                PiiMask.email(user.email()),
                user.fullName(),
                user.status().name(),
                user.onboardingStatus().name(),
                user.emailVerifiedAt() != null,
                user.createdAt(),
                user.lastLoginAt(),
                user.lockedUntil(),
                user.catCount(),
                user.scanCount(),
                user.highestPackage());
    }
}
