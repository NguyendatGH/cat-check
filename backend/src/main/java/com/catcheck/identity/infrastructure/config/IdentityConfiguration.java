package com.catcheck.identity.infrastructure.config;

import com.catcheck.audit.application.PiiRedactor;
import com.catcheck.identity.domain.OtpCodeGenerator;
import com.catcheck.identity.domain.PasswordPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityAuthProperties.class)
public class IdentityConfiguration {

    private static final String DEFAULT_ENCODER_ID = "bcrypt";

    @Bean
    public PasswordEncoder passwordEncoder(IdentityAuthProperties properties) {
        Map<String, PasswordEncoder> encoders = Map.of(
                DEFAULT_ENCODER_ID, new BCryptPasswordEncoder(properties.bcryptStrength()));
        return new DelegatingPasswordEncoder(DEFAULT_ENCODER_ID, encoders);
    }

    @Bean
    public PasswordPolicy passwordPolicy(IdentityAuthProperties properties) {
        return new PasswordPolicy(
                CommonPasswordListLoader.load(properties.commonPasswordListMaxAgeDays()));
    }

    @Bean
    public OtpCodeGenerator otpCodeGenerator(IdentityAuthProperties properties) {
        return new OtpCodeGenerator(properties.otpCodeLength());
    }

    @Bean
    public PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }
}
