package com.catcheck.credit.infrastructure;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
public class CreditConfiguration {

    @Bean
    public SecureRandom creditSecureRandom() {
        return new SecureRandom();
    }
}
