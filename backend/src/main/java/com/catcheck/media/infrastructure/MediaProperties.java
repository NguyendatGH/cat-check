package com.catcheck.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

/**
 * Cấu hình backend lưu trữ {@code LOCAL}.
 *
 * <p>A3 không được sửa {@code application.yml} (không thuộc phạm vi sở hữu file), nên mọi
 * thuộc tính đều có {@link DefaultValue} để ứng dụng vẫn khởi động được. Ở môi trường thật,
 * W3 phải khai {@code catcheck.storage.local.*} cho từng profile — đặc biệt
 * {@link #urlSigningKey()}, xem {@code docs/handovers/A3.md}.</p>
 *
 * @param root                 thư mục gốc chứa tệp; đường dẫn tương đối được phép (mặc định
 *                             nằm cạnh JAR để chạy dev được ngay)
 * @param publicBaseUrl        tiền tố URL mà {@code presignedUrl} ghép vào
 * @param urlSigningKey        khoá HMAC ký URL có hạn. RỖNG ⇒ {@code presignedUrl} ném lỗi
 *                             thay vì phát URL không ký.
 * @param maxImageBytes        trần kích thước một ảnh
 * @param allowedContentTypes  MIME type được chấp nhận
 * @param stagingTtl           thời gian sống của ảnh staging chưa chốt
 */
@ConfigurationProperties(prefix = "catcheck.storage.local")
public record MediaProperties(
        @DefaultValue("./data/media") Path root,
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue("") String urlSigningKey,
        @DefaultValue("5242880") long maxImageBytes,
        @DefaultValue({"image/jpeg", "image/png", "image/webp"}) Set<String> allowedContentTypes,
        @DefaultValue("PT30M") Duration stagingTtl
) {

    public MediaProperties {
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            allowedContentTypes = Set.of("image/jpeg", "image/png", "image/webp");
        }
        if (stagingTtl == null) {
            stagingTtl = Duration.ofMinutes(30);
        }
    }
}
