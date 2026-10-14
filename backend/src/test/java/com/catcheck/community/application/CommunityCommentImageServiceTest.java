package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.domain.CommunityCommentRef;
import com.catcheck.community.domain.port.CommunityRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.StoredImage;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommunityCommentImageServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final StorageKey KEY = new StorageKey("community-comment/2026/10/14/a.png");

    private final CommunityService communityService = mock(CommunityService.class);
    private final CommunityRepository repository = mock(CommunityRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);

    private CommunityCommentImageService service(AccountStatus status) {
        return new CommunityCommentImageService(communityService, repository, id -> status, storage);
    }

    private static CommunityPostImageService.ImageInput image(String type, long size) {
        return new CommunityPostImageService.ImageInput(type, new ByteArrayInputStream(new byte[8]), size);
    }

    @Test
    void storesImageThenCreatesCommentWithItsKey() {
        StoredImage stored = mock(StoredImage.class);
        when(stored.key()).thenReturn(KEY);
        when(storage.put(eq(CommunityCommentImageService.NAMESPACE), any(ImageUpload.class))).thenReturn(stored);

        service(AccountStatus.ACTIVE).createComment(USER, POST_ID, null, "", image("image/png", 100));

        verify(communityService).createComment(USER, POST_ID, null, "", KEY.value());
    }

    @Test
    void storedImageIsDeletedWhenCommentFails() {
        StoredImage stored = mock(StoredImage.class);
        when(stored.key()).thenReturn(KEY);
        when(storage.put(any(), any(ImageUpload.class))).thenReturn(stored);
        when(communityService.createComment(any(), any(), any(), any(), any()))
                .thenThrow(new NotFoundException(CommunityErrorCode.COMMENT_NOT_FOUND));

        assertThatThrownBy(() -> service(AccountStatus.ACTIVE)
                .createComment(USER, POST_ID, UUID.randomUUID(), "x", image("image/png", 100)))
                .isInstanceOf(NotFoundException.class);
        verify(storage).delete(KEY);
    }

    @Test
    void rejectsWrongTypeOversizeAndRestrictedBeforeStoring() {
        assertThatThrownBy(() -> service(AccountStatus.ACTIVE).createComment(USER, POST_ID, null, "x", image("image/gif", 10)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE));
        assertThatThrownBy(() -> service(AccountStatus.ACTIVE).createComment(USER, POST_ID, null, "x",
                image("image/jpeg", CommunityPostImageService.MAX_IMAGE_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE));
        assertThatThrownBy(() -> service(AccountStatus.RESTRICTED).createComment(USER, POST_ID, null, "x", image("image/png", 10)))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(storage, communityService);
    }

    @Test
    void openImageRequiresCommentOnThatPost() {
        UUID commentId = UUID.randomUUID();
        when(repository.findComment(commentId)).thenReturn(Optional.of(
                new CommunityCommentRef(commentId, POST_ID, USER, null, KEY.value())));
        when(storage.open(KEY)).thenReturn(Optional.of(new ByteArrayInputStream(new byte[]{1})));

        assertThat(service(AccountStatus.ACTIVE).openImage(POST_ID, commentId).contentType()).isEqualTo("image/png");
        assertThatThrownBy(() -> service(AccountStatus.ACTIVE).openImage(UUID.randomUUID(), commentId))
                .isInstanceOf(NotFoundException.class);
    }
}
