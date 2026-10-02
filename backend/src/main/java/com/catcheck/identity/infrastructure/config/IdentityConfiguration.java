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

/**
 * Bean dung chung cho module identity.
 *
 * <p>KHONG chay {@code @ComponentScan} cua rieng module — cac bean duoc Spring
 * tim qua {@code @SpringBootApplication} o {@code com.catcheck}. Class nay chi khai
 * bao cac bean ma domain khong duoc tu tao.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityAuthProperties.class)
public class IdentityConfiguration {

    /**
     * Id cua thuat toan mac dinh cho {@link DelegatingPasswordEncoder}.
     *
     * <p>Viet literal {@code "bcrypt"} chu khong dung
     * {@code DelegatingPasswordEncoder.defaultIdForEncode}: field do la <b>instance</b>
     * field nen khong goi duoc tu constructor, va no cung khong public (javap Spring
     * Security 7.1.1). Gia tri goc cua Spring cung la {@code "bcrypt"}.</p>
     */
    private static final String DEFAULT_ENCODER_ID = "bcrypt";

    /**
     * {@code DelegatingPasswordEncoder} de luu hash kem prefix {@code {bcrypt}} (p11 S4).
     * Khi doi sang Argon2 chi can them mot dong {@code put("argon2", ...)} va hash moi se
     * tu mang prefix moi — khong phai migrate lai toan bo hang dang chay.
     */
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

    /** Domain khong duoc mang annotation Spring (R8) nen bean o day. */
    @Bean
    public OtpCodeGenerator otpCodeGenerator(IdentityAuthProperties properties) {
        return new OtpCodeGenerator(properties.otpCodeLength());
    }

    /**
     * {@code audit..application.PiiRedactor} la lop thuan khong mang
     * {@code @Component} — dac biet module {@code audit} la named interface nen khong
     * duoc gan annotation o tang {@code application} cua no. Bean duoc khai bao o day
     * de {@code AuditLogServiceImpl} inject duoc.
     */
    @Bean
    public PiiRedactor piiRedactor() {
        return new PiiRedactor();
    }
}
