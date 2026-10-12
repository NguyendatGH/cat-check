package com.catcheck.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "catcheck.storage")
public record StorageProperties(
        @DefaultValue("auto") String provider,
        @DefaultValue Cloudinary cloudinary
) {

    public record Cloudinary(
            @DefaultValue("") String cloudName,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String apiSecret,
            @DefaultValue("catcheck") String folder
    ) {
        public Cloudinary {
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
