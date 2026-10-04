package com.catcheck.ai.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "catcheck.ai")
public record AiProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String chatModel,
        int maxContextChunks,
        int maxHistoryMessages,
        int maxTokens) {
}
