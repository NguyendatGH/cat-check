package com.catcheck.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Chọn backend lưu trữ ảnh: {@code catcheck.storage.provider} = local | cloudinary | auto.
 * {@code auto} dùng Cloudinary khi đủ cloud-name + api-key + api-secret, ngược lại dùng local.
 */
@ConfigurationProperties(prefix = "catcheck.storage")
public record StorageProperties(
        @DefaultValue("auto") String provider,
        @DefaultValue Cloudinary cloudinary
) {

    /** Thông tin tài khoản Cloudinary. api-secret là bí mật: KHÔNG log, KHÔNG đưa vào message lỗi. */
    public record Cloudinary(
            @DefaultValue("") String cloudName,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String apiSecret,
            @DefaultValue("catcheck") String folder
    ) {
        public Cloudinary {
            // Tên tài khoản hay bị dán kèm '@' hoặc khoảng trắng: chuẩn hoá thay vì để Cloudinary trả 401.
            cloudName = cloudName == null ? "" : cloudName.trim().replaceFirst("^@+", "");
            apiKey = apiKey == null ? "" : apiKey.trim();
            apiSecret = apiSecret == null ? "" : apiSecret.trim();
        }

        public boolean complete() {
            return !isBlank(cloudName) && !isBlank(apiKey) && !isBlank(apiSecret);
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
