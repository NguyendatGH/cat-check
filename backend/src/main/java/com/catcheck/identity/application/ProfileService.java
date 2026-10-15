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
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StorageKey;
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
    private final ImageStorage imageStorage;

    public ProfileService(UserAccountRepository accountRepository,
                          UserIdentityRepository identityRepository,
                          PiiCipher piiCipher,
                          AuditLogService auditLogService,
                          Clock clock,
                          @org.springframework.beans.factory.annotation.Value(
                                  "${catcheck.identity.avatar-storage-dir:target/avatars}")
                          String avatarDirectory,
                          ImageStorage imageStorage) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.piiCipher = piiCipher;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.avatarDirectory = Path.of(avatarDirectory);
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    /** Email hien tai cua tai khoan (co the da doi sau khi dang nhap). */
    public String currentEmail(UUID userId) {
        return accountRepository.findById(userId)
                .map(account -> account.email().value())
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
    }

    public ProfileView profile(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        byte[] phone = decryptPhone(account);
        List<UserIdentity> linkedIdentities = identityRepository.findByUserId(userId);
        return ProfileView.of(account, phone, linkedIdentities);
    }

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

    @Transactional(readOnly = true)
    public NotificationPreferences notificationPreferences(UUID userId) {
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        return accountRepository.findNotificationPreferences(userId)
                .orElseGet(NotificationPreferences::defaults);
    }

    @Transactional
    public NotificationPreferences updateNotificationPreferences(
            UUID userId, NotificationPreferences preferences) {
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));

        accountRepository.updateNotificationPreferences(userId, preferences);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("NOTIFICATION_PREFS_UPDATED")
                .build());

        return notificationPreferences(userId);
    }

    @Transactional
    public String uploadAvatar(UUID userId, byte[] content, String contentType) {
        if (contentType == null || !AVATAR_ALLOWED_TYPES.contains(contentType)) {
            throw new BusinessRuleException(IdentityErrorCode.AVATAR_INVALID);
        }
        if (content == null || content.length == 0 || content.length > AVATAR_MAX_BYTES) {
            throw new BusinessRuleException(IdentityErrorCode.AVATAR_INVALID);
        }

        var stored = imageStorage.put(AVATAR_NAMESPACE,
                new ImageUpload(new java.io.ByteArrayInputStream(content), null, contentType, content.length));
        String storageKey = stored.key().value();
        StorageProvider provider = StorageProvider.valueOf(stored.provider());
        UserAccount previous = accountRepository.findById(userId).orElse(null);

        accountRepository.updateAvatar(userId, storageKey, provider);
        if (previous != null && previous.avatarStorageKey() != null) {
            removeAvatarFile(previous.avatarStorageKey());
        }
        return storageKey;
    }

    @Transactional(readOnly = true)
    public AvatarData readAvatar(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        if (account.avatarStorageKey() == null) {
            throw new NotFoundException(IdentityErrorCode.NOT_FOUND);
        }
        String key = account.avatarStorageKey();
        if (isManagedKey(key)) {
            try (java.io.InputStream in = imageStorage.open(new StorageKey(key))
                    .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND))) {
                return new AvatarData(in.readAllBytes(), contentTypeOf(key));
            } catch (IOException ex) {
                throw new UncheckedIOException("Khong doc duoc anh dai dien", ex);
            }
        }
        try {
            byte[] content = Files.readAllBytes(avatarDirectory.resolve(key));
            return new AvatarData(content, contentTypeOf(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong doc duoc anh dai dien", ex);
        }
    }

    @Transactional
    public void deleteAvatar(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        if (account.avatarStorageKey() != null) {
            removeAvatarFile(account.avatarStorageKey());
        }
        accountRepository.updateAvatar(userId, null, null);
    }

    private static final String AVATAR_NAMESPACE = "user-avatar";

    private static boolean isManagedKey(String key) {
        return key.startsWith(AVATAR_NAMESPACE + "/");
    }

    private void removeAvatarFile(String key) {
        if (isManagedKey(key)) {
            imageStorage.delete(new StorageKey(key));
            return;
        }
        try {
            Files.deleteIfExists(avatarDirectory.resolve(key));
        } catch (IOException ex) {
            throw new UncheckedIOException("Khong xoa duoc anh dai dien", ex);
        }
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
            Instant createdAt,
            boolean hasAvatar) {

        static ProfileView of(UserAccount account, byte[] phone, List<UserIdentity> identities) {
            boolean hasPassword = identities.stream().anyMatch(UserIdentity::hasPassword);
            List<String> providers = identities.stream()
                    .map(identity -> identity.provider().name())
                    .toList();
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
                    hasPassword,
                    providers,
                    account.createdAt(),
                    account.avatarStorageKey() != null);
        }
    }

    public record IdentityView(
            String provider,
            String providerEmail,
            boolean emailVerified,
            Instant lastUsedAt) {
    }

    public record AvatarData(byte[] content, String contentType) {
    }
}
