package com.catcheck.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

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
 * <p><b>Con thieu so voi spec</b> (ngoai pham vi ban sua nay): ma tran role tung endpoint
 * {@code R:<ROLE>} (p8 §8.4.10, p14 §14.2.2) va step-up TOTP cho admin
 * ({@code S1:TOTP} — thieu ⇒ {@code 403 ADMIN_TOTP_REQUIRED}). O day chi chan tho
 * {@code /api/v1/admin/**} theo nhom role admin/DPO de khong con mo toang; phan quyen chi
 * tiet phai lam o tang method.</p>
 */
@Configuration
public class SecurityConfig {

    private static final String[] ADMIN_ROLES = {
            "ADMIN_SUPPORT", "ADMIN_CATALOG", "ADMIN_SUPER", "DPO"
    };

    @Bean
    @Order(3)
    public SecurityFilterChain apiFilterChain(
            HttpSecurity http,
            CookieCsrfTokenRepository csrfTokenRepository,
            CsrfTokenRequestAttributeHandler csrfTokenRequestAttributeHandler) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(csrfTokenRequestAttributeHandler))
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

                        // K2 (p8 §8.4.11, cot Auth = "—"): probe trang thai he thong cho banner
                        // bao tri — SPA phai doc duoc KHI CHUA dang nhap. Reader phia sau luon
                        // loc `secret = false` nen khong ro ri key nhay cam cua app_setting.
                        .requestMatchers(HttpMethod.GET, "/api/v1/system/status").permitAll()

                        .requestMatchers("/api/v1/admin/**").hasAnyRole(ADMIN_ROLES)
                        .anyRequest().authenticated());
        return http.build();
    }
}
