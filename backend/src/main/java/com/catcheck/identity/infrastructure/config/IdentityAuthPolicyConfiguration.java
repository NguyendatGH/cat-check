package com.catcheck.identity.infrastructure.config;

import com.catcheck.identity.domain.AuthPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IdentityAuthPolicyConfiguration {

    @Bean
    public AuthPolicy authPolicy(IdentityAuthProperties properties) {
        return new AuthPolicy(
                properties.otpTtl(),
                properties.otpMaxAttempts(),
                properties.otpTicketTtl(),
                properties.otpCodeLength(),
                properties.bcryptStrength(),
                properties.passwordMinLength(),
                properties.passwordMaxLength(),
                properties.lockoutThresholdShort(),
                properties.lockoutDurationShort(),
                properties.lockoutThresholdLong(),
                properties.lockoutDurationLong(),
                properties.sessionMaxInactive(),
                properties.sessionDefaultMaxInactive(),
                properties.maxConcurrentSessions());
    }
}
