package com.catcheck.media.api;

import java.util.Objects;

public record StoredImage(
        StorageKey key,
        String provider,
        long sizeBytes,
        String contentType
) {

    public StoredImage {
        Objects.requireNonNull(key, "storedImage.key phải có giá trị");
        Objects.requireNonNull(provider, "storedImage.provider phải có giá trị");
    }
}
