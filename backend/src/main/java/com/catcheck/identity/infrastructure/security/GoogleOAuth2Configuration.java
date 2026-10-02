package com.catcheck.identity.infrastructure.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Luong Google OAuth2 that — khi {@code spring-boot-starter-oauth2-client} co mat tren
 * classpath (B11 da chot, them dependency vao pom).
 *
 * <p><b>Backend-driven redirect</b> (p11 §11.4.1): Spring Security {@code oauth2Login()}
 * lam toan bo — Authorization Code + PKCE, verify {@code id_token} qua JWKS cua Google
 * (issuer-uri tu discovery). KHONG co {@code POST /auth/oauth/google} — luong OAuth la
 * redirect, khong phai API call tu SPA.</p>
 *
 * <p>Class nay chi kich hoat khi dependency oauth2-client co mat. Khi chua co,
 * {@link MockOAuth2Controller} phuc vu luong gia.</p>
 *
 * <p>Can them vao application.yml (W3):</p>
 * <pre>
 * spring:
 *   security:
 *     oauth2:
 *       client:
 *         registration:
 *           google:
 *             client-id: ${GOOGLE_OAUTH_CLIENT_ID}
 *             client-secret: ${GOOGLE_OAUTH_CLIENT_SECRET}
 *             scope: [openid, email, profile]
 *             redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
 *         provider:
 *           google:
 *             issuer-uri: https://accounts.google.com
 * </pre>
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.security.oauth2.client.registration.ClientRegistrationRepository")
public class GoogleOAuth2Configuration {

    @Bean
    @Order(2)
    public SecurityFilterChain oauth2FilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/oauth2/**", "/login/oauth2/**")
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .oauth2Login(oauth2 -> oauth2
                        .loginPage("/oauth2/authorization/google"));
        return http.build();
    }
}
