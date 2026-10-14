package com.catcheck.community.application;

import com.catcheck.community.api.CommunityErrorCode;
import com.catcheck.community.application.spi.AccountStatus;
import com.catcheck.community.domain.CommunityPost;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CommunityPostImageServiceTest {

    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();
    private static final StorageKey NEW_KEY = new StorageKey("community-post/2026/10/14/new.png");

    private final CommunityRepository repository = mock(CommunityRepository.class);
    private final ImageStorage storage = mock(ImageStorage.class);

    private CommunityPostImageService service(AccountStatus status) {
        return new CommunityPostImageService(repository, id -> status, storage);
    }

    private static CommunityPost post(UUID author, String imageRef) {
        return new CommunityPost(POST_ID, author, "A", "TIP", "Title", "Body", List.of(), imageRef,
                0, 0, false, false, Instant.parse("2026-10-14T10:00:00Z"));
    }

    private static CommunityPostImageService.ImageInput png(long size) {
        return new CommunityPostImageService.ImageInput("image/png", new ByteArrayInputStream(new byte[16]), size);
    }

    @Test
    void authorReplacesImageAndOldObjectIsDeleted() {
        StorageKey oldKey = new StorageKey("community-post/2026/10/01/old.jpg");
        when(repository.findPost(AUTHOR, POST_ID))
                .thenReturn(Optional.of(post(AUTHOR, oldKey.value())), Optional.of(post(AUTHOR, NEW_KEY.value())));
        when(storage.put(eq(CommunityPostImageService.NAMESPACE), any(ImageUpload.class)))
                .thenReturn(new StoredImage(NEW_KEY, "LOCAL", 16, "image/png"));

        CommunityPost result = service(AccountStatus.ACTIVE).replaceImage(AUTHOR, POST_ID, png(16));

        verify(repository).updatePostImage(POST_ID, NEW_KEY.value());
        verify(storage).delete(oldKey);
        assertThat(result.imageUrl()).isEqualTo(NEW_KEY.value());
    }

    @Test
    void nonAuthorIsForbiddenAndNothingIsStored() {
        when(repository.findPost(any(), eq(POST_ID))).thenReturn(Optional.of(post(UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service(AccountStatus.ACTIVE).replaceImage(AUTHOR, POST_ID, png(16)))
                .isInstanceOfSatisfying(PermissionDeniedException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(CommunityErrorCode.POST_IMAGE_FORBIDDEN));
        verifyNoInteractions(storage);
        verify(repository, never()).updatePostImage(any(), any());
    }

    @Test
    void missingOrHiddenPostIs404() {
        when(repository.findPost(any(), eq(POST_ID))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(AccountStatus.ACTIVE).replaceImage(AUTHOR, POST_ID, png(16)))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(storage);
    }

    @Test
    void restrictedAccountCannotUpload() {
        assertThatThrownBy(() -> service(AccountStatus.RESTRICTED).replaceImage(AUTHOR, POST_ID, png(16)))
                .isInstanceOf(PermissionDeniedException.class);
        verifyNoInteractions(repository, storage);
    }

    @Test
    void rejectsUnsupportedTypeAndOversizedFile() {
        when(repository.findPost(any(), eq(POST_ID))).thenReturn(Optional.of(post(AUTHOR, null)));
        CommunityPostImageService service = service(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.replaceImage(AUTHOR, POST_ID, new CommunityPostImageService.ImageInput(
                "image/gif", new ByteArrayInputStream(new byte[16]), 16)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE));
        assertThatThrownBy(() -> service.replaceImage(AUTHOR, POST_ID, png(CommunityPostImageService.MAX_IMAGE_BYTES + 1)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE));
        verifyNoInteractions(storage);
    }

    @Test
    void legacyUrlIsNotTreatedAsStorageKey() {
        assertThat(CommunityPostImageService.storageKeyOf("https://cdn.example/x.jpg")).isEmpty();
        assertThat(CommunityPostImageService.storageKeyOf(null)).isEmpty();
        assertThat(CommunityPostImageService.storageKeyOf(NEW_KEY.value())).contains(NEW_KEY);
    }
}
