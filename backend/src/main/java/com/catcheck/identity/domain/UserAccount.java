package com.catcheck.identity.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Anh chup cua {@code app_user} (p4 §A1) tai thoi diem doc tu DB.
 *
 * <p>La {@code record} immutable thuần Java, KHONG phai entity JPA: repository dung
 * {@code JdbcTemplate} nen khong co quan he chu the nao giua domain va ORM (R7:
 * chi {@code ..infrastructure.persistence} duoc dung Spring Data).</p>
 *
 * <p>Luu y {@code phone}: la ciphertext AES-256-GCM
 * ({@code [1 byte key_version][12 byte IV][ciphertext][16 byte tag]}) chu khong phai
 * ban ro. Xem {@code com.catcheck.identity.infrastructure.crypto.AesGcmPiiCipher}.
 * {@code byte[]} trong record so sanh theo identity nen khong duoc dung
 * {@code equals}/{@code hashCode} de so sanh noi dung — so sanh bang cach giai ma
 * o tang application.</p>
 */
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

    /** p11 §11.1.9: chi {@code ACTIVE} duoc phep xac thuc tai khoan. */
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasVerifiedEmail() {
        return emailVerifiedAt != null;
    }

    /**
     * {@code processing_restricted_at} chi dung o {@code RESTRICTED} (CHECK
     * {@code ck_app_user_restricted_pair}) — ham nay la cach doc cung nghiep vu
     * de code khong phai so sanh enum o nhieu noi.
     */
    public boolean isProcessingRestricted() {
        return status == UserStatus.RESTRICTED;
    }

    /**
     * Khoa tam thoi do {@code failed_login_count} vuot nguong. Tra {@code null} neu
     * khoa het han, de caller phai tu phan biet "khong khoa" voi "het han".
     */
    public Instant effectiveLockUntil(Instant now) {
        if (status != UserStatus.LOCKED || lockedUntil == null) {
            return null;
        }
        return lockedUntil.isAfter(now) ? lockedUntil : null;
    }

    /** p11 §11.1.1: moi tai khoan it nhat mot identity LOCAL, LOCAL thi co mat khau. */
    public boolean requiresPassword() {
        return true;
    }
}
