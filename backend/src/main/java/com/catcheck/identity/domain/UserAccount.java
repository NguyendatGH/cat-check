package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record UserAccount(
        UUID id,
        EmailAddress email,
        Instant emailVerifiedAt,
        String fullName,
        byte[] phone,
        Integer phoneKeyVersion,
        String avatarStorageKey,
        StorageProvider avatarStorageProvider,
        AppLocale locale,
        String timezone,
        UserStatus status,
        OnboardingStatus onboardingStatus,
        int failedLoginCount,
        Instant lockedUntil,
        Instant passwordChangedAt,
        Instant forcePasswordResetAt,
        Instant processingRestrictedAt,
        UUID pseudonymId,
        String notificationPrefsJson,
        String referralCodeRaw,
        Instant lastLoginAt,
        Instant deletionScheduledAt,
        Instant anonymizedAt,
        Instant createdAt,
        Instant updatedAt) {

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasVerifiedEmail() {
        return emailVerifiedAt != null;
    }

    public boolean isProcessingRestricted() {
        return status == UserStatus.RESTRICTED;
    }

    public Instant effectiveLockUntil(Instant now) {
        if (status != UserStatus.LOCKED || lockedUntil == null) {
            return null;
        }
        return lockedUntil.isAfter(now) ? lockedUntil : null;
    }

    public boolean requiresPassword() {
        return true;
    }
}
