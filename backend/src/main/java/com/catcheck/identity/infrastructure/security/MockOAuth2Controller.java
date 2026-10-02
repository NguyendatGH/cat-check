package com.catcheck.identity.infrastructure.security;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.application.AuthRequestContext;
import com.catcheck.identity.application.OAuthService;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.time.Clock;
import java.util.Map;

/**
 * <b>MOCK</b> luong Google OAuth — chi khi B11 (client ID) chua co.
 *
 * <p>Phuc vu hai endpoint ma Spring Security {@code oauth2Login()} se phuc vu khi co
 * dependency that:</p>
 * <ul>
 *   <li>{@code GET /oauth2/authorization/google} — redirect sang trang consent gia.</li>
 *   <li>{@code GET /login/oauth2/code/google} — callback gia, tao user mock va dang nhap.</li>
 * </ul>
 *
 * <p><b>KHONG dung o prod.</b> Khi B11 chot, them {@code spring-boot-starter-oauth2-client}
 * va {@code GoogleOAuth2Configuration} se thay the class nay. Class nay chi kich hoat
 * khi {@code catcheck.oauth2.mock-enabled=true} (mac dinh {@code true} o local/dev).</p>
 */
@Controller
@RequestMapping
@Profile("!prod")
@ConditionalOnProperty(name = "catcheck.oauth2.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockOAuth2Controller {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final SpringSecuritySessionGateway sessionGateway;
    private final Clock clock;

    public MockOAuth2Controller(UserAccountRepository accountRepository,
                               UserIdentityRepository identityRepository,
                               UserRoleRepository roleRepository,
                               AuditLogService auditLogService,
                               SpringSecuritySessionGateway sessionGateway,
                               Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
        this.sessionGateway = sessionGateway;
        this.clock = clock;
    }

    /**
     * Bat dau luong OAuth gia — redirect sang trang consent gia (HTML tho, khong can
     * template engine). Trang nay co nut "Dang nhap bang Google (mock)".
     */
    @GetMapping("/oauth2/authorization/google")
    public String start() {
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head><title>Google OAuth (MOCK)</title></head>
                <body>
                <h1>Dang nhap Google (che do MOCK)</h1>
                <p>Day la luong gia khi B11 (Google Client ID) chua duoc cau hinh.</p>
                <form method="get" action="/login/oauth2/code/google">
                <label>Email: <input type="email" name="email" value="mock.user@gmail.com" required></label><br>
                <label>Ten: <input type="text" name="name" value="Mock User"></label><br>
                <button type="submit">Dang nhap bang Google (mock)</button>
                </form>
                </body>
                </html>
                """;
    }

    /**
     * Callback gia — tao user mock va dang nhap. Trong luong that, day la noi Spring
     * Security goi lai sau khi doi code thanh cong.
     */
    @GetMapping("/login/oauth2/code/google")
    public RedirectView callback(@RequestParam("email") String email,
                                 @RequestParam(value = "name", required = false) String name) {
        String sub = "mock-sub-" + email.hashCode();
        Map<String, Object> claims = Map.of(
                "sub", sub,
                "email", email,
                "email_verified", true,
                "name", name == null ? "Mock User" : name);

        OAuthService oauthService = new OAuthService(
                accountRepository, identityRepository, roleRepository,
                auditLogService, clock);

        OAuthService.OAuthResult result = oauthService.findOrCreate(claims,
                AuthRequestContext.UNKNOWN);

        sessionGateway.establish(result.userId(), result.email().value(), result.roles(), true);
        return new RedirectView("/");
    }
}
