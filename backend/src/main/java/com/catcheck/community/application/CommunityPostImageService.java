package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityPost;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class CommunityPostImageService {

    public static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

    static final String NAMESPACE = "community-post";

    static final List<String> ALLOWED_CONTENT_TYPES = List.of("image/jpeg", "image/png", "image/webp");

    private final CommunityRepository repository;
    private final UserAccountPort userAccountPort;
    private final ImageStorage imageStorage;

    public CommunityPostImageService(CommunityRepository repository, UserAccountPort userAccountPort,
                                     ImageStorage imageStorage) {
        this.repository = repository;
        this.userAccountPort = userAccountPort;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public CommunityPost replaceImage(UUID userId, UUID postId, ImageInput input) {
        AccountStatus status = userAccountPort.statusOf(userId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CommunityErrorCode.ACCOUNT_RESTRICTED);
        }
        CommunityPost post = repository.findPost(userId, postId)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_NOT_FOUND));
        if (!post.authorUserId().equals(userId)) {
            throw new PermissionDeniedException(CommunityErrorCode.POST_IMAGE_FORBIDDEN);
        }

        String contentType = normalize(input.contentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, contentType);
        }
        if (input.sizeBytes() > MAX_IMAGE_BYTES) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE, MAX_IMAGE_BYTES, input.sizeBytes());
        }

        var stored = imageStorage.put(NAMESPACE, new ImageUpload(input.content(), null, contentType, input.sizeBytes()));
        repository.updatePostImage(postId, stored.key().value());

        storageKeyOf(post.imageUrl())
                .filter(previous -> !previous.equals(stored.key()))
                .ifPresent(imageStorage::delete);

        return repository.findPost(userId, postId)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public ImageContent openImage(UUID postId) {
        CommunityPost post = repository.findPost(null, postId)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_NOT_FOUND));
        StorageKey key = storageKeyOf(post.imageUrl())
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_IMAGE_NOT_FOUND));
        InputStream content = imageStorage.open(key)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.POST_IMAGE_NOT_FOUND));
        return new ImageContent(content, contentTypeOf(key));
    }

    static Optional<StorageKey> storageKeyOf(String ref) {
        if (ref == null || ref.isBlank() || ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("/")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new StorageKey(ref));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    static String contentTypeOf(StorageKey key) {
        String value = key.value().toLowerCase(Locale.ROOT);
        if (value.endsWith(".png")) return "image/png";
        if (value.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    static String normalize(String contentType) {
        if (contentType == null) return "";
        int parametersStart = contentType.indexOf(';');
        String base = parametersStart < 0 ? contentType : contentType.substring(0, parametersStart);
        return base.trim().toLowerCase(Locale.ROOT);
    }

    public record ImageInput(String contentType, InputStream content, long sizeBytes) {
    }

    public record ImageContent(InputStream content, String contentType) {
    }
}
