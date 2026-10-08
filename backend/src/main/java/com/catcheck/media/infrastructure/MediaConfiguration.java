package com.catcheck.media.infrastructure;

import com.catcheck.media.api.ImageStorage;
import com.catcheck.shared.id.UuidV7;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.util.Locale;

/**
 * Đăng ký cấu hình media và chọn đúng MỘT {@link ImageStorage} chính ({@code @Primary}) theo
 * {@code catcheck.storage.provider}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MediaProperties.class, StorageProperties.class})
public class MediaConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MediaConfiguration.class);

    @Bean
    @Primary
    public ImageStorage imageStorage(StorageProperties properties, MediaProperties media,
                                     LocalImageStorage local, Clock clock, UuidV7 uuidV7) {
        return select(properties, local, () -> new CloudinaryImageStorage(
                properties.cloudinary(), media, clock, uuidV7));
    }

    /** Logic chọn provider, tách ra để test không cần Spring. */
    static ImageStorage select(StorageProperties properties, LocalImageStorage local,
                               java.util.function.Supplier<ImageStorage> cloudinary) {
        String mode = properties.provider() == null ? "auto" : properties.provider().trim().toLowerCase(Locale.ROOT);
        boolean complete = properties.cloudinary().complete();
        switch (mode) {
            case "local":
                return local;
            case "cloudinary":
                if (!complete) {
                    throw new IllegalStateException("catcheck.storage.provider=cloudinary nhưng thiếu cấu hình: "
                            + "cần CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET");
                }
                return new RoutingImageStorage(cloudinary.get(), local);
            case "auto":
                if (complete) {
                    return new RoutingImageStorage(cloudinary.get(), local);
                }
                log.warn("Cloudinary chưa đủ cấu hình (CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET), dùng lưu trữ local");
                return local;
            default:
                throw new IllegalStateException("catcheck.storage.provider không hợp lệ: " + mode
                        + " (chỉ nhận local | cloudinary | auto)");
        }
    }
}
