package com.catcheck.media.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;

public interface ImageStorage {

    String provider();

    StoredImage put(String namespace, ImageUpload upload);

    StagedUpload stage(String namespace, ImageUpload upload, Duration ttl);

    StoredImage commit(StagedUpload staged);

    StoredImage putVariant(StorageKey parent, ImageVariant variant, ImageUpload upload);

    Optional<java.io.InputStream> open(StorageKey key);

    java.net.URI presignedUrl(StorageKey key, Duration ttl);

    boolean exists(StorageKey key);

    void delete(StorageKey key);

    void deleteVariants(StorageKey parent);

    void deleteOlderThan(Instant cutoff);

    int deleteExpiredStaging(Instant now);

    Stream<StorageKey> list(String namespace);

    record StagedUpload(StorageKey stagedKey, String contentType, long sizeBytes, Instant expiresAt) {
    }
}
