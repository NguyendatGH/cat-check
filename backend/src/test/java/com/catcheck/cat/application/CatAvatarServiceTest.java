package com.catcheck.cat.application;

import com.catcheck.cat.application.spi.UserAccountPort;
import com.catcheck.cat.domain.AvatarStorageProvider;
import com.catcheck.cat.domain.Cat;
import com.catcheck.cat.domain.port.CatRepository;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CatAvatarServiceTest {

    private CatRepository catRepository;
    private ImageStorage imageStorage;
    private UserAccountPort userAccountPort;
    private ApplicationEventPublisher eventPublisher;
    private Clock clock;
    private CatAvatarService catAvatarService;

    @BeforeEach
    void setUp() {
        catRepository = mock(CatRepository.class);
        imageStorage = mock(ImageStorage.class);
        userAccountPort = mock(UserAccountPort.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        clock = Clock.fixed(Instant.parse("2026-10-21T12:00:00Z"), ZoneOffset.UTC);

        catAvatarService = new CatAvatarService(
                catRepository,
                imageStorage,
                userAccountPort,
                eventPublisher,
                clock
        );
    }

    @Test
    @DisplayName("currentAvatarUrl trả về empty thay vì throw khi storage presignedUrl bị lỗi (STORAGE_IO_ERROR)")
    void currentAvatarUrl_whenPresignedUrlThrows_returnsEmpty() {
        Cat cat = Cat.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mèo Mun",
                LocalDate.of(2025, 1, 1),
                null,
                "CC-VN-ABCDEF",
                LocalDate.of(2026, 10, 21),
                Instant.parse("2026-10-21T12:00:00Z")
        );
        cat.attachAvatar("cat-avatar/2026/10/04/sample.jpg", AvatarStorageProvider.LOCAL, Instant.parse("2026-10-21T12:00:00Z"));

        when(imageStorage.presignedUrl(any(StorageKey.class), any(Duration.class)))
                .thenThrow(new BusinessRuleException(MediaErrorCode.STORAGE_IO_ERROR, "url-signing-key chưa được cấu hình"));

        Optional<URI> result = catAvatarService.currentAvatarUrl(cat);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("currentAvatarUrl trả về URI thành công khi storage hoạt động bình thường")
    void currentAvatarUrl_whenSuccess_returnsUri() {
        Cat cat = Cat.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mèo Mun",
                LocalDate.of(2025, 1, 1),
                null,
                "CC-VN-ABCDEF",
                LocalDate.of(2026, 10, 21),
                Instant.parse("2026-10-21T12:00:00Z")
        );
        cat.attachAvatar("cat-avatar/2026/10/04/sample.jpg", AvatarStorageProvider.LOCAL, Instant.parse("2026-10-21T12:00:00Z"));

        URI expectedUri = URI.create("http://localhost:8080/api/v1/media/sample.jpg?expires=123&sig=abc");
        when(imageStorage.presignedUrl(eq(new StorageKey("cat-avatar/2026/10/04/sample.jpg")), any(Duration.class)))
                .thenReturn(expectedUri);

        Optional<URI> result = catAvatarService.currentAvatarUrl(cat);

        assertThat(result).contains(expectedUri);
    }

    @Test
    @DisplayName("currentAvatarUrl trả về empty khi mèo không có avatar")
    void currentAvatarUrl_whenNoAvatar_returnsEmpty() {
        Cat cat = Cat.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Mèo Mun",
                LocalDate.of(2025, 1, 1),
                null,
                "CC-VN-ABCDEF",
                LocalDate.of(2026, 10, 21),
                Instant.parse("2026-10-21T12:00:00Z")
        );

        Optional<URI> result = catAvatarService.currentAvatarUrl(cat);

        assertThat(result).isEmpty();
    }
}
