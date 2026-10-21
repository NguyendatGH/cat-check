package com.catcheck.cat.application;

import com.catcheck.cat.api.CatErrorCode;
import com.catcheck.cat.api.CatUpdatedEvent;
import com.catcheck.cat.application.spi.AccountStatus;
import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.AvatarStorageProvider;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class CatAvatarService {

    private static final Logger log = LoggerFactory.getLogger(CatAvatarService.class);

    public static final long MAX_AVATAR_BYTES = 5L * 1024 * 1024;

    private static final List<String> ALLOWED_CONTENT_TYPES =
            List.of("image/jpeg", "image/png", "image/webp");

    private static final String NAMESPACE = "cat-avatar";

    private static final Duration AVATAR_URL_TTL = Duration.ofHours(1);

    private final CatRepository catRepository;
    private final ImageStorage imageStorage;
    private final UserAccountPort userAccountPort;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CatAvatarService(
            CatRepository catRepository,
            ImageStorage imageStorage,
            UserAccountPort userAccountPort,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.catRepository = catRepository;
        this.imageStorage = imageStorage;
        this.userAccountPort = userAccountPort;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AvatarContent> openAvatar(UUID ownerId, UUID catId) {
        Cat cat = requireOwnedCat(ownerId, catId);
        Optional<String> key = cat.avatarKeyValue();
        if (key.isEmpty()) {
            return Optional.empty();
        }
        return imageStorage.open(new StorageKey(key.get()))
                .map(stream -> new AvatarContent(stream, "image/jpeg"));
    }

    @Transactional
    public AvatarResult replaceAvatar(UUID ownerId, UUID catId, AvatarUpload upload) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);

        String contentType = normalize(upload.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(
                    CatErrorCode.AVATAR_INVALID, MAX_AVATAR_BYTES, ALLOWED_CONTENT_TYPES);
        }
        if (upload.sizeBytes() > MAX_AVATAR_BYTES) {
            throw new BusinessRuleException(CatErrorCode.PAYLOAD_TOO_LARGE, MAX_AVATAR_BYTES);
        }

        var stored = imageStorage.put(NAMESPACE, new ImageUpload(
                upload.content(), null, contentType, upload.sizeBytes()));

        Instant now = clock.instant();
        Optional<String> previousKey = cat.avatarKeyValue();
        cat.attachAvatar(stored.key().value(), AvatarStorageProvider.valueOf(imageStorage.provider()),
                now);
        catRepository.save(cat);

        previousKey.filter(key -> !key.equals(stored.key().value()))
                .ifPresent(key -> imageStorage.delete(new StorageKey(key)));

        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("avatar"), null, now));
        return new AvatarResult(imageStorage.presignedUrl(stored.key(), AVATAR_URL_TTL));
    }

    @Transactional
    public void removeAvatar(UUID ownerId, UUID catId) {
        requireWriteAllowed(ownerId);
        Cat cat = requireOwnedCat(ownerId, catId);
        Optional<String> previousKey = cat.avatarKeyValue();
        if (previousKey.isEmpty()) {
            return;
        }
        Instant now = clock.instant();
        cat.detachAvatar(now);
        catRepository.save(cat);
        imageStorage.delete(new StorageKey(previousKey.get()));
        eventPublisher.publishEvent(new CatUpdatedEvent(
                catId, ownerId, Set.of("avatar"), null, now));
    }

    @Transactional(readOnly = true)
    public Optional<java.net.URI> currentAvatarUrl(Cat cat) {
        return cat.avatarKeyValue().flatMap(key -> {
            try {
                return Optional.of(imageStorage.presignedUrl(new StorageKey(key), AVATAR_URL_TTL));
            } catch (Exception ex) {
                log.warn("Không thể tạo URL avatar cho mèo {} (key={}): {}",
                        cat.getId(), key, ex.getMessage());
                return Optional.empty();
            }
        });
    }

    private Cat requireOwnedCat(UUID ownerId, UUID catId) {
        return catRepository.findByIdAndOwnerId(catId, ownerId)
                .filter(cat -> !cat.isDeleted())
                .orElseThrow(() -> new NotFoundException(CatErrorCode.CAT_NOT_FOUND));
    }

    private void requireWriteAllowed(UUID ownerId) {
        AccountStatus status = userAccountPort.statusOf(ownerId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CatErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return "";
        }
        int parametersStart = contentType.indexOf(';');
        String base = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    public record AvatarContent(InputStream content, String contentType) {
    }

    public record AvatarResult(java.net.URI avatarUrl) {
    }

    public record AvatarUpload(String contentType, InputStream content, long sizeBytes) {
    }
}
