package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.StorageKey;
import com.catcheck.shared.id.UuidV7;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryStorageTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tmp;

    @Test
    void signMatchesCloudinaryDocumentationVector() {
        String signature = CloudinaryImageStorage.sign(Map.of(
                "timestamp", "1315060510",
                "public_id", "sample_image",
                "eager", "w_400,h_300,c_pad|w_260,h_200,c_crop"), "abcd");
        assertThat(signature).isEqualTo("bfd09f95f331f558cbd1320e67aa8d488770583e");
    }

    @Test
    void publicIdAndDeliveryUrl() {
        var cfg = new StorageProperties.Cloudinary("demo", "key", "abcd", "catcheck");
        var storage = new CloudinaryImageStorage(cfg, media(), CLOCK, new UuidV7(CLOCK));
        StorageKey key = new StorageKey("cat-avatar/2026/10/08/abc.png");
        assertThat(storage.publicId(key)).isEqualTo("catcheck/cat-avatar/2026/10/08/abc");
        String url = storage.deliveryUrl(key).toString();
        assertThat(url).startsWith("https://res.cloudinary.com/demo/image/authenticated/s--")
                .endsWith("--/catcheck/cat-avatar/2026/10/08/abc.png");
        String sig = url.substring(url.indexOf("s--") + 3, url.indexOf("--/"));
        assertThat(sig).hasSize(8).matches("[A-Za-z0-9_-]{8}");
        assertThat(storage.provider()).isEqualTo("CLOUDINARY");
    }

    @Test
    void selectAutoWithoutCredentialsUsesLocal() {
        LocalImageStorage local = local();
        var props = new StorageProperties("auto", new StorageProperties.Cloudinary("", "k", "s", "catcheck"));
        ImageStorage chosen = MediaConfiguration.select(props, local, () -> {
            throw new AssertionError("không được tạo Cloudinary");
        });
        assertThat(chosen).isSameAs(local);
    }

    @Test
    void selectAutoWithCredentialsUsesRoutingAndKeepsLegacyLocal() {
        LocalImageStorage local = local();
        var props = new StorageProperties("auto", new StorageProperties.Cloudinary("demo", "k", "s", "catcheck"));
        ImageStorage chosen = MediaConfiguration.select(props, local,
                () -> new CloudinaryImageStorage(props.cloudinary(), media(), CLOCK, new UuidV7(CLOCK)));
        assertThat(chosen).isInstanceOf(RoutingImageStorage.class);
        assertThat(chosen.provider()).isEqualTo("CLOUDINARY");

        var stored = local.put("cat-avatar", new ImageUpload(
                new ByteArrayInputStream(new byte[] {1, 2, 3}), "a.png", "image/png", 3));
        assertThat(chosen.exists(stored.key())).isTrue();
        assertThat(chosen.open(stored.key())).isPresent();
        assertThat(chosen.presignedUrl(stored.key(), Duration.ofMinutes(5)).toString())
                .contains("/api/v1/media/cat-avatar/");
    }

    @Test
    void selectCloudinaryWithoutCredentialsFailsFast() {
        var props = new StorageProperties("cloudinary", new StorageProperties.Cloudinary("", "", "", "catcheck"));
        assertThatThrownBy(() -> MediaConfiguration.select(props, local(), () -> null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CLOUDINARY_CLOUD_NAME");
    }

    @Test
    void selectLocalForcesLocal() {
        LocalImageStorage local = local();
        var props = new StorageProperties("local", new StorageProperties.Cloudinary("d", "k", "s", "f"));
        assertThat(MediaConfiguration.select(props, local, () -> null)).isSameAs(local);
    }

    private MediaProperties media() {
        return new MediaProperties(tmp, "http://localhost:8080", "test-signing-key", 5_242_880L, null, null);
    }

    private LocalImageStorage local() {
        return new LocalImageStorage(media(), CLOCK, new UuidV7(CLOCK));
    }
}
