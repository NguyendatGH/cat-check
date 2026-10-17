package com.catcheck.ai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
        Duration readTimeout,
        String embeddingModelDir,
        double minVectorSimilarity,
        int maxConcurrentCalls,
        Duration circuitOpenFor) {

    static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    static final int DEFAULT_MAX_CONCURRENT_CALLS = 2;
    static final Duration DEFAULT_CIRCUIT_OPEN_FOR = Duration.ofSeconds(60);

    public AiProperties(boolean enabled, String baseUrl, String apiKey, String chatModel, int maxContextChunks,
                        int maxHistoryMessages, int maxTokens, Duration connectTimeout, Duration readTimeout) {
        this(enabled, baseUrl, apiKey, chatModel, maxContextChunks, maxHistoryMessages, maxTokens, connectTimeout,
                readTimeout, null, 0, 0, null);
    }

    /** Provider treo không được giữ luồng request vô hạn: thiếu cấu hình thì dùng mặc định hữu hạn. */
    @ConstructorBinding
    public AiProperties {
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = DEFAULT_READ_TIMEOUT;
        }
        if (minVectorSimilarity <= 0 || minVectorSimilarity > 1) {
            minVectorSimilarity = com.catcheck.ai.application.AiRuntimeConfig.DEFAULT_MIN_VECTOR_SIMILARITY;
        }
        if (maxConcurrentCalls <= 0) {
            maxConcurrentCalls = DEFAULT_MAX_CONCURRENT_CALLS;
        }
        if (circuitOpenFor == null || circuitOpenFor.isNegative() || circuitOpenFor.isZero()) {
            circuitOpenFor = DEFAULT_CIRCUIT_OPEN_FOR;
        }
    }
}
