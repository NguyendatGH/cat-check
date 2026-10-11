package com.catcheck.shared.config;

import com.catcheck.shared.i18n.MessageResolver;
import com.catcheck.shared.security.AdminAccessGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chưa đăng nhập phải nhận {@code 401 UNAUTHENTICATED}, còn đã đăng nhập mà thiếu quyền vẫn
 * {@code 403}. Chuỗi {@code /api/v1/users/**} giả lập chuỗi riêng của identity để kiểm entry point
 * áp cho mọi {@link SecurityFilterChain}, không chỉ chuỗi của {@link SecurityConfig}.
 */
@SpringJUnitWebConfig(SecurityConfigAuthenticationEntryPointTest.TestConfig.class)
class SecurityConfigAuthenticationEntryPointTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousRequestToProtectedApiIs401ProblemWithErrorCode() throws Exception {
        mockMvc.perform(get("/api/v1/cats"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void anonymousRequestToAnotherFilterChainIsAlso401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
    }

    @Test
    void anonymousRequestToAdminApiIs401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserWithoutAdminRoleStillGets403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/probe").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void signedInUserReachesProtectedApi() throws Exception {
        mockMvc.perform(get("/api/v1/cats").with(user("u").roles("USER")))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, ProbeEndpoint.class})
    static class TestConfig {

        @Bean
        CookieCsrfTokenRepository csrfTokenRepository() {
            return CookieCsrfTokenRepository.withHttpOnlyFalse();
        }

        @Bean
        CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() {
            return new CsrfTokenRequestAttributeHandler();
        }

        @Bean
        AdminAccessGate adminAccessGate() {
            return (request, response, chain) -> chain.doFilter(request, response);
        }

        @Bean
        MessageResolver messageResolver() {
            return new MessageResolver(new StaticMessageSource());
        }

        @Bean
        LocaleResolver localeResolver() {
            return new AcceptHeaderLocaleResolver();
        }

        /** Giống {@code JsonMapper} của Spring Boot: có mixin để thuộc tính ProblemDetail nằm ở gốc. */
        @Bean
        JsonMapper jsonMapper() {
            return JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();
        }

        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-11T08:00:00Z"), ZoneOffset.UTC);
        }

        @Bean
        @Order(1)
        SecurityFilterChain usersChain(HttpSecurity http) throws Exception {
            http.securityMatcher("/api/v1/users/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated());
            return http.build();
        }
    }

    @RestController
    static class ProbeEndpoint {

        @GetMapping({"/api/v1/cats", "/api/v1/users/me", "/api/v1/admin/probe"})
        String probe() {
            return "ok";
        }
    }
}
