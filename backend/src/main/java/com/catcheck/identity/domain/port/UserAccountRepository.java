package com.catcheck.identity.domain.port;

import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.identity.domain.StorageProvider;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    void insert(UserAccount account);

    void markEmailVerified(UUID userId, Instant verifiedAt);

    void updateEmail(UUID userId, String newEmail, Instant changedAt);

    void updatePasswordHash(UUID userId, String passwordHash);

    void updateLoginState(UUID userId, UserStatus status, int failedLoginCount,
                          Instant lockedUntil, Instant lastLoginAt);

    void updateRestrictionState(UUID userId, UserStatus status,
                                Instant processingRestrictedAt,
                                Instant deletionScheduledAt,
                                Instant anonymizedAt);

    void updatePasswordTimestamps(UUID userId, Instant passwordChangedAt, Instant forcePasswordResetAt);

    void updateProfile(UUID userId, String fullName, byte[] phone, Integer phoneKeyVersion,
                       AppLocale locale, String timezone);

    void updateAvatar(UUID userId, String storageKey, StorageProvider provider);

    Optional<NotificationPreferences> findNotificationPreferences(UUID userId);

    void updateNotificationPreferences(UUID userId, NotificationPreferences preferences);
}
