package com.catcheck.identity.api.dto;

import com.catcheck.identity.application.AdminUserService;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.shared.security.PiiMask;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailResponse(
        UUID userId,
        String emailMasked,
        String phoneMasked,
        String fullName,
        String status,
        String onboardingStatus,
        String locale,
        String timezone,
        boolean emailVerified,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        Instant deletionScheduledAt,
        boolean processingRestricted,
        boolean totpEnabled,
        int activeSessionCount,
        long catCount,
        long scanCount,
        String highestPackage,
        List<String> roles
) {

    public static AdminUserDetailResponse from(AdminUserService.AdminUserDetail detail) {
        AdminUserSummary summary = detail.summary();
        UserAccount account = detail.account();
        return new AdminUserDetailResponse(
                summary.userId(),
                PiiMask.email(summary.email()),
                detail.phoneMasked(),
                summary.fullName(),
                summary.status().name(),
                summary.onboardingStatus().name(),
                account.locale() == null ? null : account.locale().code(),
                account.timezone(),
                summary.emailVerifiedAt() != null,
                summary.emailVerifiedAt(),
                summary.createdAt(),
                summary.lastLoginAt(),
                summary.lockedUntil(),
                account.deletionScheduledAt(),
                account.isProcessingRestricted(),
                detail.totpEnabled(),
                detail.activeSessionCount(),
                summary.catCount(),
                summary.scanCount(),
                summary.highestPackage(),
                detail.roles().stream().map(UserRole::name).toList());
    }
}
