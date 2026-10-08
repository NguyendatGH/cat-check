package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.media.api.ImageUpload;
import com.catcheck.media.api.ImageVariant;
import com.catcheck.media.api.StorageKey;
import com.catcheck.media.api.StoredImage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Decorator tương thích ngược: ghi mới luôn vào {@code primary} (Cloudinary); đọc/exists/URL/xoá
 * ưu tiên tệp đã nằm ở đĩa local (khoá cũ), ngược lại dùng {@code primary}.
 */
public class RoutingImageStorage implements ImageStorage {

    private final ImageStorage primary;
    private final ImageStorage legacy;

    public RoutingImageStorage(ImageStorage primary, ImageStorage legacy) {
        this.primary = primary;
        this.legacy = legacy;
    }

    private ImageStorage route(StorageKey key) {
        return legacy.exists(key) ? legacy : primary;
    }

    @Override
    public String provider() {
        return primary.provider();
    }

    @Override
    public StoredImage put(String namespace, ImageUpload upload) {
        return primary.put(namespace, upload);
    }

    @Override
    public StagedUpload stage(String namespace, ImageUpload upload, Duration ttl) {
        return primary.stage(namespace, upload, ttl);
    }

    @Override
    public StoredImage commit(StagedUpload staged) {
        return route(staged.stagedKey()).commit(staged);
    }

    @Override
    public StoredImage putVariant(StorageKey parent, ImageVariant variant, ImageUpload upload) {
        return route(parent).putVariant(parent, variant, upload);
    }

    @Override
    public Optional<InputStream> open(StorageKey key) {
        return route(key).open(key);
    }

    @Override
    public URI presignedUrl(StorageKey key, Duration ttl) {
        return route(key).presignedUrl(key, ttl);
    }

    @Override
    public boolean exists(StorageKey key) {
        return legacy.exists(key) || primary.exists(key);
    }

    @Override
    public void delete(StorageKey key) {
        route(key).delete(key);
    }

    @Override
    public void deleteVariants(StorageKey parent) {
        legacy.deleteVariants(parent);
        primary.deleteVariants(parent);
    }

    @Override
    public void deleteOlderThan(Instant cutoff) {
        legacy.deleteOlderThan(cutoff);
        primary.deleteOlderThan(cutoff);
    }

    @Override
    public int deleteExpiredStaging(Instant now) {
        return legacy.deleteExpiredStaging(now) + primary.deleteExpiredStaging(now);
    }

    @Override
    public Stream<StorageKey> list(String namespace) {
        return Stream.concat(legacy.list(namespace), primary.list(namespace));
    }
}
