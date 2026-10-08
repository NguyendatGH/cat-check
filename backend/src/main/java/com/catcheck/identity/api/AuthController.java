package com.catcheck.identity.api;

import com.catcheck.privacy.spi.RegistrationConsentEvent;
import com.catcheck.identity.api.dto.LoginRequest;
import com.catcheck.identity.api.dto.OtpRequestRequest;
import com.catcheck.identity.api.dto.OtpVerifyRequest;
import com.catcheck.identity.api.dto.PasswordResetConfirmRequest;
import com.catcheck.identity.api.dto.PasswordResetRequestRequest;
import com.catcheck.identity.api.dto.ReauthRequest;
import com.catcheck.identity.api.dto.RegisterRequest;
import com.catcheck.identity.api.dto.SessionItem;
import com.catcheck.identity.api.dto.SessionResponse;
import com.catcheck.identity.api.dto.SessionUser;
import com.catcheck.identity.api.dto.TotpRecoveryRequest;
import com.catcheck.identity.api.dto.TotpVerifyRequest;
import com.catcheck.identity.application.AuthRequestContext;
import com.catcheck.identity.application.LoginService;
import com.catcheck.identity.application.MfaService;
import com.catcheck.identity.application.OtpService;
import com.catcheck.identity.application.PasswordResetService;
import com.catcheck.identity.application.ProfileService;
import com.catcheck.identity.application.RegistrationService;
import com.catcheck.identity.application.SessionService;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nhom A — Xac thuc & phien (p8 §8.4.1, 15 endpoint).
 *
 * <p>Phien that su nam o Spring Session; {@code user_device_session} la ban sao de
 * hien thi. Controller chi goi tang {@code application}, khong cham truc tiep DB.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;
    private final OtpService otpService;
    private final LoginService loginService;
    private final PasswordResetService passwordResetService;
    private final MfaService mfaService;
    private final SessionService sessionService;
    private final ProfileService profileService;
    private final AuthenticatedSessionGateway sessionGateway;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final Clock clock;

    public AuthController(RegistrationService registrationService,
                          OtpService otpService,
                          LoginService loginService,
                          PasswordResetService passwordResetService,
                          MfaService mfaService,
                          SessionService sessionService,
                          ProfileService profileService,
                          AuthenticatedSessionGateway sessionGateway,
                          AuthenticatedSessionRevoker sessionRevoker,
                          Clock clock) {
        this.registrationService = registrationService;
        this.otpService = otpService;
        this.loginService = loginService;
        this.passwordResetService = passwordResetService;
        this.mfaService = mfaService;
        this.sessionService = sessionService;
        this.profileService = profileService;
        this.sessionGateway = sessionGateway;
        this.sessionRevoker = sessionRevoker;
        this.clock = clock;
    }

    /**
     * A1 — phat cookie XSRF-TOKEN.
     *
     * <p><b>Bug thật đã sửa:</b> {@code CsrfToken} do Spring Security 6+ nạp là
     * {@code DeferredCsrfToken} — {@code CsrfFilter} KHÔNG tự ghi cookie chỉ vì request đi qua
     * filter chain; cookie chỉ được {@code CookieCsrfTokenRepository} ghi khi có nơi nào đó THẬT
     * SỰ gọi {@link CsrfToken#getToken()} để lấy giá trị (vd Thymeleaf render {@code _csrf}). App
     * này không có template render nào — nếu để method rỗng như bản gốc, deferred token không
     * bao giờ resolve, {@code CsrfFilter.saveToken()} không bao giờ chạy, và SPA gọi endpoint này
     * xong vẫn KHÔNG nhận được cookie {@code XSRF-TOKEN} — xác nhận bằng {@code curl -v}: hoàn
     * toàn không có header {@code Set-Cookie}. Chặn MỌI request ghi (POST/PUT/PATCH/DELETE) toàn
     * app, không riêng gì local. Fix: tiêm {@link CsrfToken} qua tham số (Spring tự resolve từ
     * request attribute) rồi gọi {@code .getToken()} để buộc resolve ngay trong request này.</p>
     */
    @Operation(operationId = "getAuthCsrf", summary = "Phat cookie XSRF-TOKEN")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    /** A2 — bootstrap SPA. Chua dang nhap => 200 {@code {authenticated: false}}, KHONG phai 401. */
    @Operation(operationId = "getAuthSession", summary = "Bootstrap SPA — trang thai phien")
    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal Object principal) {
        // Bug that da sua: truoc day luon tra user=null du da dang nhap — FE (SessionProvider)
        // ep nguyen response nay thanh SessionUser (ep kieu khong kiem tra), field
        // "onboarding" khong ton tai => RequireOnboarding crash "Cannot read properties of
        // undefined (reading 'completed')" ngay lan dau mo app that. Phai tra ho so that.
        if (principal instanceof com.catcheck.identity.application.AuthPrincipal auth) {
            ProfileService.ProfileView profile = profileService.profile(auth.userId());
            SessionUser user = new SessionUser(
                    profile.id(), profile.email(), profile.fullName(),
                    profile.hasAvatar() ? "/api/v1/users/me/avatar" : null,
                    profile.locale(), profile.timezone(), profile.status(),
                    profile.onboardingStatus(), profile.emailVerified(), profile.hasPassword(),
                    profile.identities());
            return new SessionResponse(
                    true,
                    user,
                    List.copyOf(auth.roles()),
                    null,
                    Instant.now(clock).toString());
        }
        return new SessionResponse(false, null, List.of(), null, Instant.now(clock).toString());
    }

    /** A3 — dang ky. Tra 202, FE chuyen sang man xac thuc OTP. */
    @Operation(operationId = "postAuthRegister", summary = "Dang ky tai khoan")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest) {
        AuthRequestContext context = context(httpRequest);
        RegistrationService.RegistrationResult result = registrationService.register(
                request.email(), request.password(), request.fullName(), request.locale(),
                request.otpTicket(), mapConsents(request.consents()), context);
        // Bug that da sua: nhanh kich hoat bang ticket tra ve authenticated=true trong body
        // nhung KHONG BAO GIO thuc su mo phien (khong co Set-Cookie session nao ca — xac nhan
        // that: goi lai /auth/session ngay sau do van tra authenticated=false). login() da lam
        // dung (goi sessionGateway.establish), register() thi quen. Vai tro luon la USER cho
        // tai khoan moi tu dang ky (roleRepository.grant(..., UserRole.USER, ...) o
        // RegistrationService, khong co duong nao khac gan vai tro luc dang ky).
        if (result.authenticated()) {
            sessionGateway.establish(result.userId(), result.email().value(),
                    Set.of(UserRole.USER.name()), false);
        }
        return Map.of(
                "userId", result.userId().toString(),
                "email", result.email().value(),
                "emailVerified", result.emailVerified(),
                "authenticated", result.authenticated());
    }

    /** A4 — gui/gui lai OTP theo purpose. */
    @Operation(operationId = "postAuthOtpRequest", summary = "Gui OTP theo muc dich")
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> requestOtp(@Valid @RequestBody OtpRequestRequest request,
                                          HttpServletRequest httpRequest) {
        AuthRequestContext context = context(httpRequest);
        OtpPurpose purpose = OtpPurpose.valueOf(request.purpose());
        OtpService.OtpRequestResult result = otpService.requestOtp(
                request.email(), purpose, null, context);
        return Map.of(
                "otpExpiresAt", result.expiresAt().toString(),
                "canResendInSeconds", result.ttlSeconds(),
                "maskedEmail", maskEmail(request.email()));
    }

    /** A5 — xac thuc OTP. Voi REGISTER_VERIFY => tao phien, tra session. */
    @Operation(operationId = "postAuthOtpVerify", summary = "Xac thuc ma OTP")
    @PostMapping("/otp/verify")
    public Map<String, Object> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        AuthRequestContext context = context(httpRequest);
        OtpPurpose purpose = OtpPurpose.valueOf(request.purpose());
        var ticket = otpService.verifyOtp(request.email(), purpose, request.code(), context);
        return Map.of(
                "otpTicket", ticket.rawValue(),
                "ticketExpiresAt", ticket.expiresAt().toString(),
                "purpose", purpose.name());
    }

    /** A6 — dang nhap email + mat khau. Tra session hoac mfaRequired. */
    @Operation(operationId = "postAuthLogin", summary = "Dang nhap email + mat khau")
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        AuthRequestContext context = context(httpRequest);
        LoginService.LoginResult result = loginService.login(
                request.email(), request.password(),
                request.rememberMe() == null || request.rememberMe(), context);

        // Admin co TOTP ACTIVE: tao phien nhung yeu cau buoc 2 (mfaLevel = NONE).
        if (mfaService.totpEnabled(result.userId())) {
            sessionGateway.establish(result.userId(), result.email().value(), result.roles(),
                    request.rememberMe() == null || request.rememberMe());
            return Map.of(
                    "mfaRequired", true,
                    "mfaMethods", List.of("TOTP"));
        }

        sessionGateway.establish(result.userId(), result.email().value(), result.roles(),
                request.rememberMe() == null || request.rememberMe());
        return Map.of(
                "authenticated", true,
                "userId", result.userId().toString(),
                "roles", result.roles());
    }

    /** A7 — dang xuat. 204. */
    @Operation(operationId = "postAuthLogout", summary = "Dang xuat")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@AuthenticationPrincipal Object principal,
                       HttpServletRequest httpRequest,
                       HttpServletResponse httpResponse) {
        if (principal instanceof com.catcheck.identity.application.AuthPrincipal auth) {
            sessionService.logout(auth.userId(), context(httpRequest));
            sessionGateway.invalidateCurrent();
        }
    }

    /** A8 — yeu cau dat lai mat khau. LUON 202, khong tiet lo email ton tai. */
    @Operation(operationId = "postAuthPasswordResetRequest", summary = "Yeu cau dat lai mat khau")
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request,
                                     HttpServletRequest httpRequest) {
        passwordResetService.request(request.email(), context(httpRequest));
    }

    /** A9 — dat mat khau moi bang otp_ticket. Thu hoi moi phien. */
    @Operation(operationId = "postAuthPasswordResetConfirm", summary = "Dat mat khau moi")
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request,
                                     HttpServletRequest httpRequest) {
        passwordResetService.confirm(request.token(), request.newPassword(), context(httpRequest));
    }

    /** A10 — buoc 2 khi dang nhap: xac thuc TOTP, nang mfaLevel len TOTP. */
    @Operation(operationId = "postAuthTotpVerify", summary = "Buoc 2 dang nhap — xac thuc TOTP")
    @PostMapping("/totp/verify")
    public Map<String, Object> verifyTotp(@Valid @RequestBody TotpVerifyRequest request,
                                          @AuthenticationPrincipal Object principal,
                                          HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        mfaService.verifyForLogin(userId, request.code(), context(httpRequest));
        // p11 §11.12.1: mfaLevel la THUOC TINH PHIEN. Truoc ban sua nay no chi la mot chuoi
        // trong JSON tra ve, nen khong co cach nao biet phien da qua TOTP hay chua va bo gac
        // /api/v1/admin/** (p8 §8.4.12) khong the ton tai.
        sessionGateway.markMfaTotpVerified();
        return Map.of("mfaLevel", "TOTP");
    }

    /** A11 — dung ma khoi phuc thay ma TOTP. Bat buoc dan toi enroll lai. */
    @Operation(operationId = "postAuthTotpRecovery", summary = "Dung ma khoi phuc")
    @PostMapping("/totp/recovery")
    public Map<String, Object> totpRecovery(@Valid @RequestBody TotpRecoveryRequest request,
                                            @AuthenticationPrincipal Object principal,
                                            HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        MfaService.RecoveryResult result = mfaService.verifyRecovery(
                userId, request.recoveryCode(), context(httpRequest));
        sessionGateway.markMfaTotpVerified();
        return Map.of(
                "mfaLevel", "TOTP",
                "enrollmentRequired", result.enrollmentRequired(),
                "recoveryCodesRemaining", result.recoveryCodesRemaining());
    }

    /** A12 — step-up re-authentication. */
    @Operation(operationId = "postAuthReauth", summary = "Step-up re-authentication")
    @PostMapping("/reauth")
    public Map<String, Object> reauth(@Valid @RequestBody ReauthRequest request,
                                      @AuthenticationPrincipal Object principal,
                                      HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        sessionService.reauthenticate(userId, request.method(), request.credential(),
                context(httpRequest));
        return Map.of(
                "scope", "WINDOW",
                "reauthExpiresAt", Instant.now(clock).plusSeconds(300).toString(),
                "method", request.method());
    }

    /** A13 — danh sach thiet bi dang dang nhap. */
    @Operation(operationId = "getAuthSessions", summary = "Danh sach thiet bi dang dang nhap")
    @GetMapping("/sessions")
    public Map<String, Object> listSessions(@AuthenticationPrincipal Object principal) {
        UUID userId = requireUserId(principal);
        List<SessionService.SessionView> sessions = sessionService.listSessions(userId);
        List<SessionItem> items = sessions.stream()
                .map(s -> new SessionItem(
                        s.id(), s.deviceLabel(), maskIp(s.ipAddress()),
                        s.lastSeenAt(), s.createdAt(), s.current()))
                .toList();
        return Map.of("items", items);
    }

    /** A14 — thu hoi mot phien khac. */
    @Operation(operationId = "deleteAuthSession", summary = "Thu hoi mot phien")
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID sessionId,
                              @AuthenticationPrincipal Object principal,
                              HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        sessionService.revokeSession(userId, sessionId, context(httpRequest));
    }

    /** A15 — dang xuat moi thiet bi (phai co phien hien tai). */
    @Operation(operationId = "postAuthSessionsRevokeAll", summary = "Dang xuat moi thiet bi")
    @PostMapping("/sessions/revoke-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@AuthenticationPrincipal Object principal,
                                  HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        sessionRevoker.revokeAllByUserId(userId);
    }

    /* ---------- Helpers ---------- */

    /** Chuyen {@code RegisterRequest.ConsentGrant} (api.dto) sang {@code RegistrationConsentEvent.ConsentGrant}
     * (api) — application layer khong nhan thang DTO request (quy uoc chung cua controller nay). */
    private static List<RegistrationConsentEvent.ConsentGrant> mapConsents(
            List<RegisterRequest.ConsentGrant> consents) {
        if (consents == null) {
            return List.of();
        }
        return consents.stream()
                .map(c -> new RegistrationConsentEvent.ConsentGrant(c.purposeCode(), c.granted()))
                .toList();
    }

    private UUID requireUserId(Object principal) {
        if (principal instanceof com.catcheck.identity.application.AuthPrincipal auth) {
            return auth.userId();
        }
        throw new com.catcheck.shared.error.BusinessRuleException(IdentityErrorCode.UNAUTHENTICATED);
    }

    private AuthRequestContext context(HttpServletRequest request) {
        return AuthRequestContext.of(
                request.getHeader("X-Request-Id"),
                request.getRemoteAddr(),
                request.getHeader("User-Agent"));
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        String[] parts = email.split("@", 2);
        String local = parts[0];
        String domain = parts[1];
        String maskedLocal = local.length() <= 1
                ? "*" : local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return maskedLocal + "@" + domain;
    }

    private static String maskIp(String ip) {
        if (ip == null) {
            return null;
        }
        int dot = ip.lastIndexOf('.');
        return dot > 0 ? ip.substring(0, dot) + ".*" : "***";
    }
}
