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

/**
 * Chuoi filter bao mat cho module identity (p11 §11.1.3, S2).
 *
 * <p><b>CSRF</b>: {@link CookieCsrfTokenRepository#withHttpOnlyFalse()} — SPA phai doc
 * duoc cookie de gan vao header {@code X-XSRF-TOKEN}, dung plain
 * {@link CsrfTokenRequestAttributeHandler} (KHONG phai {@code XorCsrfTokenRequestAttributeHandler}
 * — da thu va xac nhan that bai bang curl: handler Xor giai ma gia tri header truoc khi so sanh,
 * nhung {@code CookieCsrfTokenRepository} luon ghi gia tri THO vao cookie, nen SPA doc cookie roi
 * echo lai dung y nguyen vao header se luon bi 403. Xor chi hop voi form HTML render server-side
 * dung {@code th:action}/{@code _csrf} — app nay KHONG co template nao, xac nhan qua log khoi dong
 * "Cannot find template location"). Day la pattern SPA chinh thuc cua Spring Security cho
 * {@code CookieCsrfTokenRepository}.</p>
 *
 * <p><b>Phien</b>: Spring Session JDBC (auto-configured). Cookie
 * {@code __Host-CATCHECK_SESSION} — prefix {@code __Host-} buoc trinh duyet yeu cau
 * {@code Secure}, {@code Path=/}, KHONG co thuoc tinh {@code Domain}. KHONG bat CORS
 * o prod (p11 §11.1.2: SPA va API cung origin).</p>
 *
 * <p><b>OAuth2</b>: khi {@code spring-boot-starter-oauth2-client} co mat tren classpath
 * (B11 da chot), {@link GoogleOAuth2Configuration} kich hoat {@code oauth2Login()}.
 * Khi chuua co, {@link MockOAuth2Controller} (profile {@code !prod}) phuc vu luong gia
 * de app van boot duoc.</p>
 *
 * <p>Chuoi filter nay chi ap dung cho {@code /api/v1/auth}, {@code /api/v1/account},
 * {@code /api/v1/users} — module khac khong bi anh huong (M0 SecurityConfig permitAll van
 * cho qua con lai).</p>
 */
@Configuration
public class IdentitySecurityConfig {

    /** p11 §11.1.3: ten cookie __Host- buoc Secure + Path=/ + khong Domain. */
    public static final String SESSION_COOKIE_NAME = "__Host-CATCHECK_SESSION";

    /** Chi dung khi chay profile {@code local} (khong TLS) — xem javadoc bean ben duoi. */
    public static final String SESSION_COOKIE_NAME_LOCAL = "CATCHECK_SESSION";

    /**
     * Dat ten cookie phien bang code vi {@code server.servlet.session.cookie.name}
     * nam o application*.yml (khong duoc sua — W3 so huu). Prefix {@code __Host-}
     * buoc trinh duyet chi gui cookie qua HTTPS.
     *
     * <p><b>Bug thật đã sửa (xác nhận bằng {@code curl -v}, không có {@code Secure} thì trình
     * duyệt/RFC 6265bis từ chối lưu cookie có prefix {@code __Host-} — và {@code Secure} chỉ được
     * set khi kết nối chính là HTTPS):</b> `local` profile chạy backend/frontend qua HTTP thường
     * (README "Chạy dev nhanh" — không có TLS, Caddy chỉ dùng ở staging/prod). Cookie
     * {@code __Host-CATCHECK_SESSION} vì vậy KHÔNG BAO GIỜ được trình duyệt lưu khi chạy local —
     * đăng nhập xong request tiếp theo vẫn là ẩn danh, coi như chưa đăng nhập, dù server trả 200.
     * Chỉ khắt khe {@code __Host-} ở staging/prod (có TLS qua Caddy) — profile {@code local} dùng
     * tên cookie thường, không đổi bất cứ gì khác về bảo mật phiên (vẫn HttpOnly, SameSite qua
     * cấu hình Spring Session mặc định).</p>
     */
    @Bean
    @Profile("!local")
    public ServletContextInitializer sessionCookieInitializer() {
        return servletContext -> servletContext.getSessionCookieConfig()
                .setName(SESSION_COOKIE_NAME);
    }

    /** Xem javadoc {@link #sessionCookieInitializer()} — bản nới cho local, không có TLS. */
    @Bean
    @Profile("local")
    public ServletContextInitializer sessionCookieInitializerLocal() {
        return servletContext -> servletContext.getSessionCookieConfig()
                .setName(SESSION_COOKIE_NAME_LOCAL);
    }

    /**
     * CSRF token repository — cookie {@code XSRF-TOKEN} khong HttpOnly de SPA doc duoc.
     * p11 §11.1.3: {@code Secure; SameSite=Lax; Path=/}, KHONG {@code HttpOnly}.
     */
    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    /**
     * Plain {@code CsrfTokenRequestAttributeHandler} — bug thật đã sửa, xem javadoc lớp ở đầu
     * file. {@code XorCsrfTokenRequestAttributeHandler} là mặc định của Spring Security 7 cho
     * app render HTML server-side (chống BREACH), nhưng phá vỡ pattern SPA đọc cookie + echo
     * header vì nó kỳ vọng giá trị submit đã được XOR-hoá còn cookie luôn lưu giá trị thô.
     */
    @Bean
    public CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler() {
        return new CsrfTokenRequestAttributeHandler();
    }

    /**
     * Chuoi filter cho identity — thap hon M0 SecurityConfig (permitAll) de duoc kiem
     * tra truoc. Chi ap dung cho path cua module identity.
     */
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
