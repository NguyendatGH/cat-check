package com.catcheck.identity.infrastructure.security;

import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
public class IdentitySecurityConfig {

    public static final String SESSION_COOKIE_NAME = "__Host-CATCHECK_SESSION";

    public static final String SESSION_COOKIE_NAME_LOCAL = "CATCHECK_SESSION";

    @Bean
    @Profile("!local")
    public ServletContextInitializer sessionCookieInitializer() {
        return servletContext -> servletContext.getSessionCookieConfig()
                .setName(SESSION_COOKIE_NAME);
    }

    @Bean
    @Profile("local")
    public ServletContextInitializer sessionCookieInitializerLocal() {
        return servletContext -> servletContext.getSessionCookieConfig()
                .setName(SESSION_COOKIE_NAME_LOCAL);
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    public CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    @Bean
    @Order(1)
    public SecurityFilterChain identityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/v1/auth/**", "/api/v1/account/**", "/api/v1/users/**")
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/csrf",
                                "/api/v1/auth/session",
                                "/api/v1/auth/register",
                                "/api/v1/auth/otp/**",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password-reset/**",
                                "/api/v1/auth/totp/**",
                                "/api/v1/auth/reauth",
                                "/api/v1/auth/sessions/**").permitAll()
                        .anyRequest().authenticated());
        return http.build();
    }
}
