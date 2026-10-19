package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record AdminUserSummary(
        UUID userId,
        String email,
        String fullName,
        UserStatus status,
        OnboardingStatus onboardingStatus,
        Instant emailVerifiedAt,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lockedUntil,
        long catCount,
        long scanCount,
        String highestPackage
) {
}
