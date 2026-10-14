package com.catcheck.credit.infrastructure;

import com.catcheck.credit.application.WelcomeBonusProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WelcomeBonusProperties.class)
public class CreditConfiguration {

    @Bean
    public SecureRandom creditSecureRandom() {
        return new SecureRandom();
    }
}
