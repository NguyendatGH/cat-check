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

/**
 * Cong vao {@code app_user}. Trien khai o {@code ..infrastructure.persistence}
 * bang {@code JdbcTemplate} (R7).
 */
public interface UserAccountRepository {

    Optional<UserAccount> findById(UUID userId);

    Optional<UserAccount> findByEmail(EmailAddress email);

    boolean existsByEmail(EmailAddress email);

    void insert(UserAccount account);

    void markEmailVerified(UUID userId, Instant verifiedAt);

    /**
     * Doi email dang nhap (p8 B8). Chi goi sau khi OTP {@code EMAIL_CHANGE} da duoc
     * xac minh tren dia chi moi — email la khoa chinh nen khong bao gio tu tay.
     */
    void updateEmail(UUID userId, String newEmail, Instant changedAt);

    /**
     * Ghi mat khau moi ({@code user_identity.password_hash}, p11 S4). Tach khoi
     * {@link #updatePasswordTimestamps} vi no o bang khac — {@code user_identity} khong
     * phai cua {@code app_user}.
     */
    void updatePasswordHash(UUID userId, String passwordHash);

    /**
     * Cap nhat phan khoa dang nhap: {@code status}, {@code failed_login_count},
     * {@code locked_until}, {@code last_login_at}. Gop vao mot lenh de khong can
     * {@code SELECT ... FOR UPDATE} o tang application.
     *
     * <p>Cot {@code processing_restricted_at} / {@code deletion_scheduled_at} /
     * {@code anonymized_at} <b>khong</b> nam trong lenh nay: p4 §A1 rang buoc CHECK
     * chung cho {@code status} (vi du {@code status = 'ANONYMIZED'} yeu cau
     * {@code anonymized_at IS NOT NULL}), nen tang nghiep vu khong duoc ghi do la bat
     * buoc khi chi doi {@code status} sang {@code LOCKED}/{@code ACTIVE} — neu ghi
     * {@code NULL} o day se lam mat rang buoc. Xem
     * {@link #updateRestrictionState}.</p>
     */
    void updateLoginState(UUID userId, UserStatus status, int failedLoginCount,
                          Instant lockedUntil, Instant lastLoginAt);

    /**
     * Ghi nhan han che xu ly / xoa tai khoan — dung khi doi {@code status} sang
     * {@code RESTRICTED} / {@code DELETION_REQUESTED} / {@code ANONYMIZED} de bao dam
     * CHECK cua p4 §A1.
     */
    void updateRestrictionState(UUID userId, UserStatus status,
                                Instant processingRestrictedAt,
                                Instant deletionScheduledAt,
                                Instant anonymizedAt);

    /**
     * Ghi moc doi mat khau. {@code forcePasswordResetAt} phai lon hon
     * {@code passwordChangedAt} neu khac null (CHECK {@code ck_app_user_force_reset}) —
     * {@code passwordChangedAt} chinh la moc do lai.
     */
    void updatePasswordTimestamps(UUID userId, Instant passwordChangedAt, Instant forcePasswordResetAt);

    void updateProfile(UUID userId, String fullName, byte[] phone, Integer phoneKeyVersion,
                       AppLocale locale, String timezone);

    void updateAvatar(UUID userId, String storageKey, StorageProvider provider);

    /**
     * Doc {@code app_user.notification_prefs} (B11). {@code Optional.empty()} chi khi
     * KHONG co dong {@code app_user} nao — con dong co that nhung JSON rong/thieu field thi
     * tra ve {@link NotificationPreferences#defaults()} da dien du bay co.
     */
    Optional<NotificationPreferences> findNotificationPreferences(UUID userId);

    /**
     * Ghi bay co vao {@code app_user.notification_prefs} theo kieu <b>merge</b> (B12): field
     * la nam san trong JSONB (co thu nghiem, co cua phien ban sau) phai duoc giu nguyen, khong
     * bi ghi de mat. Xem {@code JdbcUserAccountRepository} de biet merge chay bang mot cau
     * {@code UPDATE} duy nhat.
     */
    void updateNotificationPreferences(UUID userId, NotificationPreferences preferences);
}
