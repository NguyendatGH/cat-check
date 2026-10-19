package com.catcheck.media.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MediaPropertiesTest {

    private static final long SCAN_MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    @Test
    void defaultImageCapCoversTheScanUploadLimit() {
        MediaProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("catcheck.storage.local", MediaProperties.class);

        assertThat(defaults.maxImageBytes()).isGreaterThanOrEqualTo(SCAN_MAX_UPLOAD_BYTES);
    }
}
