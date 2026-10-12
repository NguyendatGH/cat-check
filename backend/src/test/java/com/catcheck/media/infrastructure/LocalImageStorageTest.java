package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.MediaErrorCode;
import com.catcheck.media.api.StoredImage;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalImageStorageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1};

    @TempDir
    Path root;

    @Test
    void htmlDeclaredAsPngIsRejectedAndNothingIsWritten() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> storage().put("cat-avatar", upload(html, "image/png")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE);
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void jpegBytesDeclaredAsPngAreRejected() {
        assertThatThrownBy(() -> storage().put("cat-avatar", upload(JPEG, "image/png")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(MediaErrorCode.STORAGE_UNSUPPORTED_TYPE);
    }

    @Test
    void realPngIsStoredWithItsFullContent() throws Exception {
        LocalImageStorage storage = storage();

        StoredImage stored = storage.put("cat-avatar", upload(PNG, "image/png"));

        assertThat(stored.sizeBytes()).isEqualTo(PNG.length);
        try (var in = storage.open(stored.key()).orElseThrow()) {
            assertThat(in.readAllBytes()).as("doc thu dau tep khong duoc lam mat byte nao").isEqualTo(PNG);
        }
    }

    @Test
    void commitKeepsTheNamespaceOfTheStagedUpload() {
        LocalImageStorage storage = storage();
        ImageStorage.StagedUpload staged = storage.stage("cat-avatar", upload(JPEG, "image/jpeg"), Duration.ofMinutes(5));

        StoredImage committed = storage.commit(staged);

        assertThat(committed.key().value()).startsWith("cat-avatar/2026/10/11/");
        assertThat(storage.exists(committed.key())).isTrue();
    }

    private LocalImageStorage storage() {
        return new LocalImageStorage(
                new MediaProperties(root, "http://localhost:8080", "test-signing-key", 5_242_880L, null, null),
                CLOCK, new UuidV7(CLOCK));
    }

    private static ImageUpload upload(byte[] bytes, String contentType) {
        return new ImageUpload(new ByteArrayInputStream(bytes), null, contentType, bytes.length);
    }
}
