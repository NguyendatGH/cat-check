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

    /** M25: lỗi nghiệp vụ (quá cỡ) giữa chừng chép vẫn phải dọn tệp tạm {@code .part}. */
    @Test
    void oversizedUploadLeavesNoTemporaryFile() throws Exception {
        LocalImageStorage storage = storage("test-signing-key", PNG.length - 1L);

        assertThatThrownBy(() -> storage.put("cat-avatar", upload(PNG, "image/png")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(MediaErrorCode.STORAGE_PAYLOAD_TOO_LARGE);
        try (var files = Files.walk(root)) {
            assertThat(files.filter(Files::isRegularFile).toList()).isEmpty();
        }
    }

    @Test
    void validSignatureOpensTheFile() throws Exception {
        LocalImageStorage storage = storage();
        StoredImage stored = storage.put("cat-avatar", upload(PNG, "image/png"));
        long expires = CLOCK.instant().getEpochSecond() + 60;

        assertThat(storage.openSigned(stored.key(), expires, hmac("test-signing-key", stored.key().value(), expires)))
                .isPresent()
                .get().satisfies(media -> media.content().close());
    }

    /** M27: khoá ký rỗng/trắng ⇒ ai cũng tự tính được HMAC — phải từ chối, không mở tệp. */
    @Test
    void blankSigningKeyFailsClosed() throws Exception {
        for (String blankKey : new String[] {"", "   "}) {
            LocalImageStorage storage = storage(blankKey, 5_242_880L);
            StoredImage stored = storage.put("cat-avatar", upload(PNG, "image/png"));
            long expires = CLOCK.instant().getEpochSecond() + 60;
            String forged = blankKey.isEmpty() ? "00" : hmac(blankKey, stored.key().value(), expires);

            assertThat(storage.openSigned(stored.key(), expires, forged)).isEmpty();
        }
    }

    private static String hmac(String signingKey, String keyValue, long expires) throws Exception {
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return java.util.HexFormat.of().formatHex(
                mac.doFinal((keyValue + "\n" + expires).getBytes(StandardCharsets.UTF_8)));
    }

    private LocalImageStorage storage() {
        return storage("test-signing-key", 5_242_880L);
    }

    private LocalImageStorage storage(String signingKey, long maxImageBytes) {
        return new LocalImageStorage(
                new MediaProperties(root, "http://localhost:8080", signingKey, maxImageBytes, null, null),
                CLOCK, new UuidV7(CLOCK));
    }

    private static ImageUpload upload(byte[] bytes, String contentType) {
        return new ImageUpload(new ByteArrayInputStream(bytes), null, contentType, bytes.length);
    }
}
