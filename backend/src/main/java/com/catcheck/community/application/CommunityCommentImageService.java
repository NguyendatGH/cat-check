package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.application.spi.UserAccountPort;
import com.catcheck.community.domain.CommunityComment;
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
import java.util.UUID;

/**
 * Comments (and replies) with an attached image. Uses the same storage, content types and size cap
 * as post images; the stored object is removed again if the comment cannot be created.
 */
@Service
public class CommunityCommentImageService {

    static final String NAMESPACE = "community-comment";

    private final CommunityService communityService;
    private final CommunityRepository repository;
    private final UserAccountPort userAccountPort;
    private final ImageStorage imageStorage;

    public CommunityCommentImageService(CommunityService communityService, CommunityRepository repository,
                                        UserAccountPort userAccountPort, ImageStorage imageStorage) {
        this.communityService = communityService;
        this.repository = repository;
        this.userAccountPort = userAccountPort;
        this.imageStorage = imageStorage;
    }

    @Transactional
    public CommunityComment createComment(UUID authorId, UUID postId, UUID parentCommentId, String body,
                                          CommunityPostImageService.ImageInput image) {
        if (image == null) {
            return communityService.createComment(authorId, postId, parentCommentId, body, null);
        }
        AccountStatus status = userAccountPort.statusOf(authorId);
        if (status == null || !status.allowsWrite()) {
            throw new PermissionDeniedException(CommunityErrorCode.ACCOUNT_RESTRICTED);
        }
        String contentType = CommunityPostImageService.normalize(image.contentType());
        if (!CommunityPostImageService.ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE, contentType);
        }
        if (image.sizeBytes() > CommunityPostImageService.MAX_IMAGE_BYTES) {
            throw new BusinessRuleException(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE,
                    CommunityPostImageService.MAX_IMAGE_BYTES, image.sizeBytes());
        }
        StorageKey key = imageStorage.put(NAMESPACE,
                new ImageUpload(image.content(), null, contentType, image.sizeBytes())).key();
        try {
            return communityService.createComment(authorId, postId, parentCommentId, body, key.value());
        } catch (RuntimeException ex) {
            imageStorage.delete(key);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public CommunityPostImageService.ImageContent openImage(UUID postId, UUID commentId) {
        String ref = repository.findComment(commentId)
                .filter(comment -> comment.postId().equals(postId))
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.COMMENT_NOT_FOUND))
                .imageUrl();
        StorageKey key = CommunityPostImageService.storageKeyOf(ref)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.COMMENT_IMAGE_NOT_FOUND));
        InputStream content = imageStorage.open(key)
                .orElseThrow(() -> new NotFoundException(CommunityErrorCode.COMMENT_IMAGE_NOT_FOUND));
        return new CommunityPostImageService.ImageContent(content, CommunityPostImageService.contentTypeOf(key));
    }
}
