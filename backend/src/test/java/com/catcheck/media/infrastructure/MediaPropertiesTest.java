package com.catcheck.media.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trần mặc định của media không được chặt hơn trần nghiệp vụ lớn nhất của module gọi.
 *
 * <p>Lỗi cũ: scan nhận ảnh tới 8 MB ({@code ScanThresholds.MAX_UPLOAD_BYTES}, cũng là con số
 * {@code GET /scan/config} báo cho client) nhưng media chặn ở 5 MB — ảnh 5–8 MB chạy xong pipeline
 * rồi mới nổ {@code STORAGE_PAYLOAD_TOO_LARGE}, tức người có gói lưu ảnh không quét được ảnh đó.
 * Avatar tự giữ trần 5 MB riêng ({@code CatAvatarService}, {@code ProfileService}).</p>
 */
class MediaPropertiesTest {

    private static final long SCAN_MAX_UPLOAD_BYTES = 8L * 1024 * 1024;

    @Test
    void defaultImageCapCoversTheScanUploadLimit() {
        MediaProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("catcheck.storage.local", MediaProperties.class);

        assertThat(defaults.maxImageBytes()).isGreaterThanOrEqualTo(SCAN_MAX_UPLOAD_BYTES);
    }
}
