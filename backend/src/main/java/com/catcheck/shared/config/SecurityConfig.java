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

/**
 * Chuoi filter mac dinh cho toan bo phan con lai cua API (moi path khong thuoc
 * {@code IdentitySecurityConfig} @Order(1) va {@code GoogleOAuth2Configuration} @Order(2)).
 *
 * <p>Chuoi nay dung chung {@code CookieCsrfTokenRepository} voi chuoi identity (SPA gui token doc tu
 * cookie {@code XSRF-TOKEN}) va mac dinh {@code authenticated()}: {@code IdentitySecurityConfig} chi
 * match 3 prefix nen moi path con lai, ke ca {@code /api/v1/admin/**}, roi vao day.</p>
 *
 * <p><b>Cot Auth</b> lay theo p8 §8.3.2: {@code —} = cong khai khong can phien, {@code U} = can
 * phien hop le. Danh muc cong khai la nhom F (p8 §8.4.6) + C16 {@code GET /policies/{code}} +
 * {@code /care-tips}. Rieng F6 {@code GET /reference/health-survey/{version}} la {@code U} nen
 * phai dat truoc rule {@code /reference/**}.</p>
 *
 * <p><b>Dieu kien chung cua p8 §8.4.12 cho {@code /api/v1/admin/**}</b> gom hai lop, cai o
 * day theo dung thu tu cua p8 §8.3.1 buoc 4a (vai tro truoc, MFA sau):</p>
 * <ol>
 *   <li>{@code hasAnyRole(ADMIN_ROLES)} — loc tho theo nhom role admin/DPO;</li>
 *   <li>{@link AdminAccessGate} — doi {@code mfaLevel = TOTP} trong phien, thieu ⇒
 *       {@code 403 ADMIN_TOTP_REQUIRED}, chua enroll ⇒ {@code 403 TOTP_SETUP_REQUIRED}
 *       (p11 §11.12.1). Gac nay gan bang {@code addFilterAfter(..., AuthorizationFilter.class)}
 *       nen chay SAU buoc (1): user thuong go thu URL admin van nhan 403 thong thuong chu khong
 *       phai mot thong bao he lo rang khu vuc admin ton tai va chi con thieu 2FA.</li>
 * </ol>
 *
 * <p><b>Con thieu so voi spec</b> (ngoai pham vi ban sua nay): ma tran role tung endpoint
 * {@code R:<ROLE>} (p8 §8.4.12, p14 §14.2.2) duoc kiem o tang controller cua tung module
 * (xem cac {@code *AdminRoleGuard}), va step-up {@code SW}/{@code S1} cho tung hanh dong.</p>
 */
@Configuration
public class SecurityConfig {

    private static final String[] ADMIN_ROLES = {
            "ADMIN_SUPPORT", "ADMIN_CATALOG", "ADMIN_SUPER", "DPO", "MODERATOR"
    };

    /**
     * K1 {@code POST /csp-report} va L73 {@code POST /client-errors} (p8 §8.4.11, §8.4.12).
     * Hai endpoint nay <b>cong khai VA mien CSRF</b>, va ca hai dieu do deu la ban chat cua
     * chung chu khong phai noi long cho tien:
     * <ul>
     *   <li>Bao cao vi pham CSP do <b>chinh trinh duyet</b> gui ({@code report-to}), khong phai
     *       do JavaScript cua SPA — khong co cach nao dinh kem token CSRF vao do.</li>
     *   <li>L73 nhan loi runtime cua frontend, va p8 ghi ro ly do phai cong khai: "loi tai
     *       chunk xay ra TRUOC khi co phien". Chua co phien thi cung chua co cookie
     *       {@code XSRF-TOKEN}, nen doi CSRF token o day nghia la dung bao cao duoc dung loi
     *       can bao cao nhat.</li>
     * </ul>
     * Doi lai, ca hai chi {@code INSERT}/ghi log mot ban ghi da loc PII, khong doc hay doi
     * trang thai nghiep vu nao cua nguoi dung — nen mot request gia mao khong lam duoc gi
     * ngoai viec tu ghi mot dong log, va dieu do da bi rate limit theo IP chan (60/phut va
     * 10/phut, p8 §8.4.11 / §8.4.12).
     */
    private static final String[] PUBLIC_INGEST_PATHS = {
            "/api/v1/csp-report", "/api/v1/client-errors"
    };

    /**
     * Chua dang nhap ⇒ {@code 401 UNAUTHENTICATED} (xem {@link ProblemAuthenticationEntryPoint}).
     * Khai bao thanh bean {@code Customizer<ExceptionHandlingConfigurer<HttpSecurity>>} chu khong
     * goi {@code exceptionHandling(...)} trong {@link #apiFilterChain}: Spring Security 7 ap bean
     * kieu nay cho MOI {@link HttpSecurity}, nen chuoi rieng cua identity ({@code /api/v1/users/**})
     * cung tra 401 ma {@code shared} khong phai import {@code identity}.
     */
    @Bean
    public Customizer<ExceptionHandlingConfigurer<HttpSecurity>> unauthenticatedEntryPoint(
            MessageResolver messageResolver, LocaleResolver localeResolver, JsonMapper jsonMapper, Clock clock) {
        ProblemAuthenticationEntryPoint entryPoint =
                new ProblemAuthenticationEntryPoint(messageResolver, localeResolver, jsonMapper, clock);
        return exceptions -> exceptions.authenticationEntryPoint(entryPoint);
    }

    /**
     * Mo Actuator cho curl/script o profile {@code local}. {@code exposure.include} chi dang ky endpoint;
     * {@link #apiFilterChain} chi {@code permitAll} {@code /actuator/health/**} va {@code /actuator/info},
     * con {@code /actuator/env} (seed-demo.py doc o {@code require_local_profile()}) bi chan vi can phien.
     *
     * <p><b>Vi sao mo thanh mot chuoi rieng {@code @Profile("local")} chu khong them mot dong
     * {@code permitAll} vao chuoi chung:</b> mot dong {@code permitAll("/actuator/**")} trong
     * {@link #apiFilterChain} se co hieu luc o <b>moi</b> profile, ke ca {@code prod} — va
     * {@code /actuator/env} in ra toan bo Environment (bao gom ten bien moi truong chua
     * secret). Dat ca luat vao mot chuoi chi ton tai khi profile {@code local} duoc bat khien
     * {@code prod} <b>khong co cach nao</b> thua huong no: bean khong duoc tao thi khong co
     * chuoi nao de nho. p18 §18.9 giu nguyen: o staging/prod actuator chi mo trong mang noi bo
     * va {@code application-prod.yml} van gioi han {@code exposure.include} o muc toi thieu.
     */
    @Bean
    @Order(0)
    @Profile("local")
    public SecurityFilterChain actuatorLocalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                // Khong CSRF va khong phien: Actuator o may dev duoc goi bang curl/script
                // (seed-demo.py), khong phai bang form trong trinh duyet.
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
                        // Ha tang: health-check cua Docker/Caddy, tai lieu API, trang loi noi bo.
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()

                        // F6 la `U` — phai dat TRUOC rule /reference/** ben duoi.
                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/health-survey/**").authenticated()

                        // Nhom F (p8 §8.4.6) + C16 + care-tips: cong khai, chi doc.
                        .requestMatchers(HttpMethod.GET, "/api/v1/reference/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/care-tips/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/policies/**").permitAll()
                        // Phase 2/3 public catalogues: reads do not expose account data.
                        .requestMatchers(HttpMethod.GET, "/api/v1/community/posts/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/places/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/shop/products/**").permitAll()

                        // C1 (p8 §8.4.3, cot Auth = "—"): danh muc `consent_purpose`. Bug that
                        // da sua — truoc day roi vao `anyRequest().authenticated()`, nen man
                        // DANG KY (M1 01c-1) khong tai duoc 5 checkbox dong y: nguoi dang dang
                        // ky theo dinh nghia la chua co phien. Bat duoc bang e2e route-sweep
                        // (403 /api/v1/privacy/purposes tai /auth/register).
                        // Chi doc, chi tra nhan/mo ta/mandatory/sensitive — khong co du lieu ca nhan.
                        .requestMatchers(HttpMethod.GET, "/api/v1/privacy/purposes").permitAll()

                        // K2 (p8 §8.4.11, cot Auth = "—"): probe trang thai he thong cho banner
                        // bao tri — SPA phai doc duoc KHI CHUA dang nhap. Reader phia sau luon
                        // loc `secret = false` nen khong ro ri key nhay cam cua app_setting.
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/status").permitAll()

                        // K1 + L73 — xem javadoc PUBLIC_INGEST_PATHS. Gioi han dung POST:
                        // khong ai duoc DOC lai nhung gi da gui vao hai endpoint nay.
                        .requestMatchers(HttpMethod.POST, PUBLIC_INGEST_PATHS).permitAll()

                        .requestMatchers("/api/v1/admin/**").hasAnyRole(ADMIN_ROLES)
                        // URL đã có HMAC + hạn dùng; không cần session để thẻ mèo và <img> tải được.
                        .requestMatchers(HttpMethod.GET, "/api/v1/media/**").permitAll()
                        .anyRequest().authenticated())

                // p8 §8.4.12 dieu kien chung #1. Dat SAU AuthorizationFilter de buoc kiem role
                // chay truoc (p8 §8.3.1 buoc 4a). Gac tu bo qua moi path khong bat dau bang
                // /api/v1/admin nen khong anh huong duong nghiep vu.
                .addFilterAfter(adminAccessGate, AuthorizationFilter.class);
        return http.build();
    }
}
