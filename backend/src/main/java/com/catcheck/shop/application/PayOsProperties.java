package com.catcheck.shop.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "catcheck.shop.payos")
public record PayOsProperties(String clientId, String apiKey, String checksumKey,
                              Integer timeoutMs, Integer maxRetries, Duration paymentTtl) {

    public PayOsProperties {
        timeoutMs = timeoutMs == null ? 10_000 : timeoutMs;
        maxRetries = maxRetries == null ? 0 : maxRetries;
        paymentTtl = paymentTtl == null ? Duration.ofMinutes(5) : paymentTtl;
    }

    /** All three keys must be present; otherwise the shop runs with payments disabled. */
    public boolean configured() {
        return hasText(clientId) && hasText(apiKey) && hasText(checksumKey);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
