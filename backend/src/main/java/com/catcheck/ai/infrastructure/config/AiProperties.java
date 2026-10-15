package com.catcheck.ai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "catcheck.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String chatModel,
        int maxContextChunks,
        int maxHistoryMessages,
        int maxTokens,
        Duration connectTimeout,
        Duration readTimeout) {

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

    /** Provider treo không được giữ luồng request vô hạn: thiếu cấu hình thì dùng mặc định hữu hạn. */
    public AiProperties {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
    }
}
