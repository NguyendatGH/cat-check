package com.catcheck.place.infrastructure.goong;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "catcheck.place.goong")
public record GoongProperties(
        String apiKey,
        String baseUrl,
        String keyword,
        Integer resultLimit,
        Integer dailyRequestBudget,
        Duration connectTimeout,
        Duration readTimeout) {

    /** Thiếu cấu hình thì dùng mặc định hữu hạn; apiKey trống = tắt nguồn Goong. Integer: biến env để trống ("") không làm app không khởi động. */
    public GoongProperties {
        apiKey = apiKey == null ? "" : apiKey.strip();
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://rsapi.goong.io";
        if (keyword == null || keyword.isBlank()) keyword = "thú y";
        if (resultLimit == null || resultLimit <= 0) resultLimit = 10;
        // 900/ngày ≈ 27k/tháng, dưới hạn mức miễn phí 30k request của Goong.
        if (dailyRequestBudget == null || dailyRequestBudget <= 0) dailyRequestBudget = 900;
        if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
            connectTimeout = Duration.ofSeconds(5);
        }
        if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
            readTimeout = Duration.ofSeconds(10);
        }
    }
}
