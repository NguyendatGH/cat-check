package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.AttentionAlertChannel;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.identity.domain.StorageProvider;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * B11/B12 với cổng trong bộ nhớ — không cần Testcontainers.
 *
 * <p>Shape là tám field phẳng của p8 §8.5, lưu ở {@code user_notification_preference}.
 * Ba điều cần chắc: (1) tài khoản chưa có dòng tuỳ chọn thì GET trả mặc định của p4 F4 chứ
 * không nổ — nếu nổ thì chính màn cài đặt không mở nổi để user tạo dòng đó; (2) PUT ghi đủ
 * tám field rồi đọc lại được; (3) user không tồn tại thì 404 và không ghi gì.</p>
 *
 * <p>KHÔNG còn test "tắt kênh bắt buộc bị từ chối": với shape p8 thì ràng buộc p12 §12.9.1 là
 * ràng buộc KIỂU — {@code AttentionAlertChannel} không có giá trị tắt, nên không biểu diễn
 * được trạng thái sai để mà test. Giá trị lạ bị chặn lúc parse body, xem
 * {@code UpdateNotificationPreferencesRequestTest}.</p>
 */
class ProfileServiceNotificationPreferencesTest {

    private static final UUID USER_ID = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T03:00:00Z"), ZoneOffset.UTC);

    private final FakeUserAccountRepository accounts = new FakeUserAccountRepository();
    private final List<String> auditActions = new ArrayList<>();

    private ProfileService service() {
        AuditLogService audit = event -> auditActions.add(event.action());
        // identityRepository/piiCipher khong duoc cham toi trong hai luong nay nen de null —
        // truyen fake rong chi lam nhieu test.
        return new ProfileService(accounts, null, null, audit, CLOCK, "target/avatars-test");
    }

    @Test
    void chua_co_dong_tuy_chon_thi_tra_mac_dinh_cua_p4() {
        accounts.users.add(USER_ID);

        NotificationPreferences prefs = service().notificationPreferences(USER_ID);

        assertEquals(AttentionAlertChannel.PUSH_AND_INAPP, prefs.attentionAlertChannel());
        assertTrue(prefs.creditAlertsEnabled());
        assertTrue(prefs.reportReadyEnabled());
        assertFalse(prefs.imageRetentionWarningEnabled());
        assertFalse(prefs.normalResultEnabled());
        assertTrue(prefs.quietHoursEnabled());
        assertEquals(LocalTime.of(22, 0), prefs.quietHoursStart());
        assertEquals(LocalTime.of(7, 0), prefs.quietHoursEnd());
    }

    @Test
    void khong_co_dong_app_user_thi_404() {
        assertThrows(NotFoundException.class, () -> service().notificationPreferences(USER_ID));
    }

    @Test
    void put_ghi_du_tam_field_va_tra_ve_ban_vua_ghi() {
        accounts.users.add(USER_ID);
        NotificationPreferences wanted = new NotificationPreferences(
                AttentionAlertChannel.INAPP_ONLY, false, false, true, true, false,
                LocalTime.of(23, 30), LocalTime.of(6, 15));

        NotificationPreferences saved = service().updateNotificationPreferences(USER_ID, wanted);

        assertEquals(wanted, saved);
        assertEquals(wanted, accounts.preferences.get(USER_ID));
        assertEquals(List.of("NOTIFICATION_PREFS_UPDATED"), auditActions);
    }

    @Test
    void gio_im_lang_vat_qua_nua_dem_la_hop_le() {
        // p4 F4: `start > end` KHÔNG phải lỗi — 22:00 → 07:00 là trường hợp mặc định.
        accounts.users.add(USER_ID);
        NotificationPreferences wanted = new NotificationPreferences(
                AttentionAlertChannel.PUSH_AND_INAPP, true, true, false, false, true,
                LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertEquals(wanted, service().updateNotificationPreferences(USER_ID, wanted));
    }

    @Test
    void chi_in_app_van_la_lua_chon_hop_le() {
        accounts.users.add(USER_ID);
        NotificationPreferences wanted = new NotificationPreferences(
                AttentionAlertChannel.INAPP_ONLY, true, true, false, false, true,
                LocalTime.of(22, 0), LocalTime.of(7, 0));

        assertEquals(AttentionAlertChannel.INAPP_ONLY,
                service().updateNotificationPreferences(USER_ID, wanted).attentionAlertChannel());
    }

    @Test
    void put_cho_user_khong_ton_tai_thi_404() {
        assertThrows(NotFoundException.class, () -> service().updateNotificationPreferences(
                USER_ID, NotificationPreferences.defaults()));
        assertNull(accounts.preferences.get(USER_ID));
    }

    /** Chỉ hai method của B11/B12 có nghĩa; phần còn lại của cổng không được chạm tới. */
    private static final class FakeUserAccountRepository implements UserAccountRepository {

        private final java.util.Map<UUID, NotificationPreferences> preferences = new java.util.HashMap<>();
        /** Tài khoản tồn tại — tách khỏi `preferences` vì nay hai thứ nằm ở hai bảng. */
        private final java.util.Set<UUID> users = new java.util.HashSet<>();

        /**
         * Service chỉ hỏi "dòng app_user có tồn tại không", không đọc field nào — nên một bản
         * ghi tối thiểu là đủ, không cần dựng hồ sơ thật.
         */
        private static final UserAccount ACCOUNT_PRESENT = new UserAccount(
                USER_ID, null, null, null, null, null, null, null,
                AppLocale.VI, "Asia/Ho_Chi_Minh", UserStatus.ACTIVE, null,
                0, null, null, null, null, null, null, null, null, null, null, null, null);

        @Override
        public Optional<NotificationPreferences> findNotificationPreferences(UUID userId) {
            return Optional.ofNullable(preferences.get(userId));
        }

        @Override
        public void updateNotificationPreferences(UUID userId, NotificationPreferences prefs) {
            preferences.put(userId, prefs);
        }

        @Override
        public Optional<UserAccount> findById(UUID userId) {
            // Service chỉ cần biết dòng app_user có tồn tại hay không; nội dung không dùng tới.
            return users.contains(userId) ? Optional.of(ACCOUNT_PRESENT) : Optional.empty();
        }

        @Override
        public Optional<UserAccount> findByEmail(EmailAddress email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByEmail(EmailAddress email) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void insert(UserAccount account) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void markEmailVerified(UUID userId, Instant verifiedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateEmail(UUID userId, String newEmail, Instant changedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePasswordHash(UUID userId, String passwordHash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLoginState(UUID userId, UserStatus status, int failedLoginCount,
                                     Instant lockedUntil, Instant lastLoginAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateRestrictionState(UUID userId, UserStatus status,
                                           Instant processingRestrictedAt,
                                           Instant deletionScheduledAt,
                                           Instant anonymizedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updatePasswordTimestamps(UUID userId, Instant passwordChangedAt,
                                             Instant forcePasswordResetAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateProfile(UUID userId, String fullName, byte[] phone, Integer phoneKeyVersion,
                                  AppLocale locale, String timezone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAvatar(UUID userId, String storageKey, StorageProvider provider) {
            throw new UnsupportedOperationException();
        }
    }
}
