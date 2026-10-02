package com.catcheck.insight.infrastructure;

import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link InsightErrorCode} vào {@code ErrorCodeRegistry} — mỗi hằng một
 * {@code @Bean ErrorCode} riêng (đúng khuôn {@code CreditErrorCodeConfiguration}). Đã kiểm tra
 * không trùng với scan/identity/privacy/credit/colorchart.
 */
@Configuration
public class InsightErrorCodeConfiguration {

    @Bean
    ErrorCode healthFlagNotFound() {
        return InsightErrorCode.HEALTH_FLAG_NOT_FOUND;
    }

    @Bean
    ErrorCode healthFlagDuplicate() {
        return InsightErrorCode.HEALTH_FLAG_DUPLICATE;
    }
}
