package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.NotificationPreferences;
import com.catcheck.identity.domain.StorageProvider;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ho so ca nhan (p8 B1, B2, B3-B5, B9, B10, B11, B12).
 *
 * <p>{@code phone} ma hoa AES-256-GCM khoa con {@code pii.phone}, AAD
 * {@code app_user|phone|<userId>} (p11 §11.10.3). KHONG bao gio tra ban ro.</p>
 *
 * <p>Avatar: M1 luu local (StorageProvider.LOCAL). Module media (A3) se thay
 * bang Cloudinary o Phase 2 — URL sinh o tang ung dung co TTL, KHONG luu URL
 * vao DB (p4 §A1, p11 S7).</p>
 */
@Service
public class ProfileService {

    private static final String PHONE_TABLE = "app_user";
    private static final String PHONE_COLUMN = "phone";
    private static final long AVATAR_MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> AVATAR_ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp");

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final PiiCipher piiCipher;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final Path avatarDirectory;

    public ProfileService(UserAccountRepository accountRepository,
                          UserIdentityRepository identityRepository,
                          PiiCipher piiCipher,
                          AuditLogService auditLogService,
                          Clock clock,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${catcheck.identity.avatar-storage-dir:target/avatars}")
                          String avatarDirectory) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.piiCipher = piiCipher;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.avatarDirectory = Path.of(avatarDirectory);
    }

    /** B1 — ho so day du cua chinh minh. */
    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        byte[] phone = decryptPhone(account);
        return ProfileView.of(account, phone);
    }

    /** B2 — merge-patch {@code fullName}, {@code phone}, {@code locale}, {@code timezone}. */
    @Transactional
    public ProfileView updateProfile(UUID userId, String fullName, String phone,
                                     String locale, String timezone) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));

        String newFullName = fullName != null ? fullName : account.fullName();
        AppLocale newLocale = locale != null ? AppLocale.fromCode(locale) : account.locale();
        String newTimezone = timezone != null ? timezone : account.timezone();

        byte[] phoneCipher = account.phone();
        Integer phoneKeyVersion = account.phoneKeyVersion();
        if (phone != null) {
            if (phone.isBlank()) {
                phoneCipher = null;
                phoneKeyVersion = null;
            } else {
                phoneCipher = piiCipher.encrypt(CryptoPurpose.PII_PHONE,
                        PiiCipher.aad(PHONE_TABLE, PHONE_COLUMN, userId),
                        phone.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                phoneKeyVersion = piiCipher.currentWriteKeyVersion();
            }
        }

        accountRepository.updateProfile(userId, newFullName, phoneCipher, phoneKeyVersion,
                newLocale, newTimezone);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("PROFILE_UPDATED")
                .build());

        return profile(userId);
    }

    /** B9 — cac phuong thuc dang nhap da lien ket. */
    @Transactional(readOnly = true)
    public List<IdentityView> identities(UUID userId) {
        return identityRepository.findByUserId(userId).stream()
                .map(identity -> new IdentityView(
                        identity.provider().name(),
                        identity.providerEmail(),
                        identity.emailVerified(),
                        identity.lastUsedAt()))
                .toList();
    }

    /** B10 — go lien ket Google. Go identity cuoi cung => {@code 409 IDENTITY_LAST_REMAINING}. */
    @Transactional
    public void unlinkIdentity(UUID userId, String rawProvider) {
        IdentityProvider provider;
        try {
            provider = IdentityProvider.valueOf(rawProvider.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException(IdentityErrorCode.IDENTITY_NOT_FOUND);
        }

        List<UserIdentity> identities = identityRepository.findByUserId(userId);
        UserIdentity target = identities.stream()
                .filter(identity -> identity.provider() == provider)
                .findFirst()
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.IDENTITY_NOT_FOUND));

        if (identities.size() <= 1) {
            throw new BusinessRuleException(IdentityErrorCode.IDENTITY_LAST_REMAINING);
        }

        identityRepository.delete(target.id());

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("IDENTITY_UNLINKED")
                .meta("provider", provider.name())
                .build());
    }

    /**
     * B11 — đọc tuỳ chọn thông báo.
     *
     * <p>Nguồn là bảng {@code user_notification_preference} đúng như p8 B11 chỉ định (tạo ở
     * {@code V13__notification.sql}). Bản trước đọc cột JSONB {@code app_user.notification_prefs}
     * vì lúc đó V13 chưa tồn tại — nay đã có bảng thật.</p>
     *
     * <p>p4 F4 nói dòng tuỳ chọn được tạo cùng lúc với {@code app_user}, nhưng tài khoản đăng
     * ký TRƯỚC khi V13 chạy thì chưa có dòng nào. Thiếu dòng ⇒ trả {@link
     * NotificationPreferences#defaults()} (đúng DEFAULT của cột), KHÔNG ném lỗi: nếu ném thì
     * chính màn cài đặt không mở nổi để user tạo ra dòng đó.</p>
     *
     * <p>Trả thẳng record miền: nó chỉ gồm cờ boolean + giờ, không có PII hay trạng thái nội
     * bộ nào để che như {@link ProfileView}. R4 chỉ cấm controller <b>phơi</b> kiểu miền ở chữ
     * ký — controller vẫn map sang DTO.</p>
     */
    @Transactional(readOnly = true)
    public NotificationPreferences notificationPreferences(UUID userId) {
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        return accountRepository.findNotificationPreferences(userId)
                .orElseGet(NotificationPreferences::defaults);
    }

    /**
     * B12 — ghi đè toàn bộ tám field (PUT không nhận partial, p8 §8.1.11).
     *
     * <p><b>Không còn kiểm "tắt kênh bắt buộc" ở đây.</b> p12 §12.9.1 yêu cầu cảnh báo "kết
     * quả cần chú ý" không tắt hoàn toàn được; với shape p8 thì ràng buộc đó là ràng buộc
     * KIỂU — {@link com.catcheck.identity.domain.AttentionAlertChannel} chỉ có
     * {@code PUSH_AND_INAPP} và {@code INAPP_ONLY}, không có giá trị tắt. Giá trị lạ bị chặn
     * ngay lúc parse body ({@code 400 VALIDATION_FAILED}), và CHECK
     * {@code ck_unp_attention_channel} của V13 là lớp chặn cuối ở DB. Bản trước phải kiểm tay
     * vì shape JSONB cũ cho phép biểu diễn trạng thái "tắt hẳn".</p>
     */
    @Transactional
    public NotificationPreferences updateNotificationPreferences(
            UUID userId, NotificationPreferences preferences) {
        // Phải tồn tại dòng app_user mới ghi — upsert không có FK check lúc thiếu user sẽ
        // nổ bằng lỗi ràng buộc khó hiểu thay vì 404.
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));

        accountRepository.updateNotificationPreferences(userId, preferences);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("NOTIFICATION_PREFS_UPDATED")
                .build());

        return notificationPreferences(userId);
    }

    /** B4 — tai anh dai dien. Tra ve storage key (KHONG phai URL). */
    @Transactional
    public String uploadAvatar(UUID userId, byte[] content, String contentType) {
        if (contentType == null || !AVATAR_ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException(IdentityErrorCode.AVATAR_INVALID);
        }
        if (content == null || content.length == 0 || content.length > AVATAR_MAX_BYTES) {
            throw new BusinessRuleException(IdentityErrorCode.AVATAR_INVALID);
        }

        String storageKey = userId + "/" + UUID.randomUUID() + extensionOf(contentType);
        try {
            Path target = avatarDirectory.resolve(storageKey);
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong luu duoc anh dai dien", ex);
        }

        accountRepository.updateAvatar(userId, storageKey, StorageProvider.LOCAL);
        return storageKey;
    }

    /** B3 — doc anh dai dien. */
    @Transactional(readOnly = true)
    public AvatarData readAvatar(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        if (account.avatarStorageKey() == null) {
            throw new NotFoundException(IdentityErrorCode.NOT_FOUND);
        }
        try {
            byte[] content = Files.readAllBytes(avatarDirectory.resolve(account.avatarStorageKey()));
            return new AvatarData(content, contentTypeOf(account.avatarStorageKey()));
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong doc duoc anh dai dien", ex);
        }
    }

    /** B5 — go anh dai dien. */
    @Transactional
    public void deleteAvatar(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        if (account.avatarStorageKey() != null) {
            try {
                Files.deleteIfExists(avatarDirectory.resolve(account.avatarStorageKey()));
            } catch (IOException ex) {
                throw new UncheckedIOException("Khong xoa duoc anh dai dien", ex);
            }
        }
        accountRepository.updateAvatar(userId, null, null);
    }

    private byte[] decryptPhone(UserAccount account) {
        if (account.phone() == null) {
            return null;
        }
        return piiCipher.decrypt(CryptoPurpose.PII_PHONE,
                PiiCipher.aad(PHONE_TABLE, PHONE_COLUMN, account.id()),
                account.phone());
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }

    private static String contentTypeOf(String storageKey) {
        if (storageKey.endsWith(".jpg")) {
            return "image/jpeg";
        }
        if (storageKey.endsWith(".png")) {
            return "image/png";
        }
        if (storageKey.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    /** Mot dong cua {@code GET /users/me} (p8 B1). */
    public record ProfileView(
            UUID id,
            String email,
            String fullName,
            String phone,
            String locale,
            String timezone,
            String status,
            String onboardingStatus,
            boolean emailVerified,
            boolean hasPassword,
            List<String> identities,
            Instant createdAt) {

        static ProfileView of(UserAccount account, byte[] phone) {
            return new ProfileView(
                    account.id(),
                    account.email().value(),
                    account.fullName(),
                    phone == null ? null : new String(phone, java.nio.charset.StandardCharsets.UTF_8),
                    account.locale().code(),
                    account.timezone(),
                    account.status().name(),
                    account.onboardingStatus().name(),
                    account.hasVerifiedEmail(),
                    true,
                    List.of(),
                    account.createdAt());
        }
    }

    /** Mot dong cua {@code GET /account/identities} (p8 B9). */
    public record IdentityView(
            String provider,
            String providerEmail,
            boolean emailVerified,
            Instant lastUsedAt) {
    }

    /** Anh dai dien da doc — dung cho {@code GET /users/me/avatar}. */
    public record AvatarData(byte[] content, String contentType) {
    }
}
