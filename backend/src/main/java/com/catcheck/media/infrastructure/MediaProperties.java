package com.catcheck.media.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "catcheck.storage.local")
public record MediaProperties(
        @DefaultValue("./data/media") Path root,
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        @DefaultValue("") String urlSigningKey,
        @DefaultValue("8388608") long maxImageBytes,
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
