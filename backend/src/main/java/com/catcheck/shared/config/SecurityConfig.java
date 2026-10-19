package com.catcheck.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.servlet.LocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;

import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.AdminAccessGate;
import com.catcheck.shared.security.ProblemAuthenticationEntryPoint;

@Configuration
public class SecurityConfig {

    private static final String[] ADMIN_ROLES = {
            "ADMIN_SUPPORT", "ADMIN_CATALOG", "ADMIN_SUPER", "DPO", "MODERATOR"
    };

    private static final String[] PUBLIC_INGEST_PATHS = {
            "/api/v1/csp-report", "/api/v1/client-errors", "/api/v1/payments/payos/webhook"
    };

    @Bean
    public Customizer<ExceptionHandlingConfigurer<HttpSecurity>> unauthenticatedEntryPoint(
            MessageResolver messageResolver, LocaleResolver localeResolver, JsonMapper jsonMapper, Clock clock) {
        ProblemAuthenticationEntryPoint entryPoint =
                new ProblemAuthenticationEntryPoint(messageResolver, localeResolver, jsonMapper, clock);
        return exceptions -> exceptions.authenticationEntryPoint(entryPoint);
    }

    @Bean
    @Order(0)
    @Profile("local")
    public SecurityFilterChain actuatorLocalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }

    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler,
            AdminAccessGate adminAccessGate) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler)
                        .ignoringRequestMatchers(PUBLIC_INGEST_PATHS))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/health-survey/**").authenticated()

                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/care-tips/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shop/products/**").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/privacy/purposes").permitAll()

                        .requestMatchers(HttpMethod.GET, "/api/v1/system/status").permitAll()

                        .requestMatchers(HttpMethod.POST, PUBLIC_INGEST_PATHS).permitAll()

                        .requestMatchers("/api/v1/admin/**").hasAnyRole(ADMIN_ROLES)
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()
                        .anyRequest().authenticated())

                .addFilterAfter(adminAccessGate, AuthorizationFilter.class);
        return http.build();
    }
}
