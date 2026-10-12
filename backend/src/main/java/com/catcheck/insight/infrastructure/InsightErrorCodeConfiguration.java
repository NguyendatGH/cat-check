package com.catcheck.insight.infrastructure;

import com.catcheck.insight.api.InsightErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
