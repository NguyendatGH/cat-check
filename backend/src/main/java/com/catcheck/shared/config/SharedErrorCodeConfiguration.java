package com.catcheck.shared.config;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.security.AdminApiErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SharedErrorCodeConfiguration {

    @Bean
    ErrorCode adminAccessDenied() {
        return AdminApiErrorCode.ACCESS_DENIED;
    }
}
