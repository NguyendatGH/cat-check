package com.catcheck.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import com.catcheck.shared.security.AdminAccessGate;

/**
 * Chuoi filter mac dinh cho toan bo phan con lai cua API (moi path khong thuoc
 * {@code IdentitySecurityConfig} @Order(1) va {@code GoogleOAuth2Configuration} @Order(2)).
 *
 * <p><b>Bug that da sua (xac nhan bang curl tren server dang chay):</b> ban M0 cua class nay
 * chi co {@code anyRequest().permitAll()}. Hai hau qua that:</p>
 * <ol>
 *   <li><b>403 tren moi request GHI ngoai {@code /api/v1/auth}</b> — tao ho so meo
 *       ({@code POST /api/v1/cats}), scan, export... deu hong. Chuoi nay khong cau hinh CSRF nen
 *       Spring dung mac dinh {@code HttpSessionCsrfTokenRepository} (token nam trong session),
 *       trong khi SPA gui token doc tu cookie {@code XSRF-TOKEN} do
 *       {@code CookieCsrfTokenRepository} cua chuoi identity phat ra ⇒ hai ben so sanh hai
 *       nguon token khac nhau, luon lech. Dang nhap thi duoc (thuoc chuoi identity), nhung
 *       buoc ngay sau do la tao ho so meo thi 403.</li>
 *   <li><b>Khong co xac thuc</b> — {@code permitAll()} nghia la {@code /api/v1/cats},
 *       {@code /api/v1/scans}, ca {@code /api/v1/admin/**} deu mo cho nguoi dung an danh.</li>
 * </ol>
 *
 * <p>Javadoc ban M0 da ghi ro "module identity (M1+) se THAY THE class nay bang luat xac
 * thuc/phan quyen that" — viec do chua lam, va {@code IdentitySecurityConfig} lai dat
 * {@code securityMatcher} hep (chi 3 prefix) nen phan con lai roi het vao day.</p>
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
     * <b>Bug that da sua (do bang curl tren cong 8094, truoc/sau):</b>
     * {@code GET /actuator/env/spring.profiles.active} tra <b>403</b> o profile {@code local}
     * du {@code application-local.yml} da mo
     * {@code management.endpoints.web.exposure.include: health,info,env,beans,mappings,loggers,flyway}.
     *
     * <p>Nguyen nhan: {@code exposure.include} chi quyet dinh Actuator co <b>dang ky</b>
     * endpoint hay khong; viec cho vao hay khong la cua Spring Security. Chuoi
     * {@link #apiFilterChain} chi {@code permitAll} dung {@code /actuator/health/**} va
     * {@code /actuator/info}, nen {@code /actuator/env}, {@code /beans}, {@code /mappings},
     * {@code /loggers}, {@code /flyway} roi vao {@code anyRequest().authenticated()}. Va vi
     * chuoi nay khong cau hinh {@code authenticationEntryPoint} nao, Spring dung
     * {@code Http403ForbiddenEntryPoint} ⇒ <b>403, khong phai 401</b> — dau vet sai huong, lam
     * nguoi doc tuong la van de phan quyen chu khong phai thieu luat permitAll.
     *
     * <p><b>Hau qua that:</b> {@code backend/tools/seed-demo.py} dung o buoc
     * {@code require_local_profile()} (nó doc dung URL nay de tu choi chay nham vao
     * staging/prod), nen toan bo du lieu demo — scan, lich su, xu huong, health flag, export —
     * khong dung duoc. Do thuc te: ca khi DA dang nhap bang phien hop le van 403, nen ghi chu
     * "endpoint nay can phien dang nhap" trong script la mot suy doan sai.
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
