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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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
    private final Clock clock;

    public AuthController(RegistrationService registrationService,
                          OtpService otpService,
                          LoginService loginService,
                          PasswordResetService passwordResetService,
                          MfaService mfaService,
                          SessionService sessionService,
                          ProfileService profileService,
                          AuthenticatedSessionGateway sessionGateway,
                          Clock clock) {
        this.registrationService = registrationService;
        this.otpService = otpService;
        this.loginService = loginService;
        this.passwordResetService = passwordResetService;
        this.mfaService = mfaService;
        this.sessionService = sessionService;
        this.profileService = profileService;
        this.sessionGateway = sessionGateway;
        this.clock = clock;
    }

    @Operation(operationId = "getAuthCsrf", summary = "Phat cookie XSRF-TOKEN")
    @GetMapping("/csrf")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void csrf(CsrfToken csrfToken) {
        csrfToken.getToken();
    }

    @Operation(operationId = "getAuthSession", summary = "Bootstrap SPA — trang thai phien")
    @GetMapping("/session")
    public SessionResponse session(@AuthenticationPrincipal Object principal) {
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

    @Operation(operationId = "postAuthRegister", summary = "Dang ky tai khoan")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request,
                                        HttpServletRequest httpRequest) {
        AuthRequestContext context = context(httpRequest);
        RegistrationService.RegistrationResult result = registrationService.register(
                request.email(), request.password(), request.fullName(), request.locale(),
                request.otpTicket(), mapConsents(request.consents()), context);
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

    @Operation(operationId = "postAuthOtpRequest", summary = "Gui OTP theo muc dich")
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, Object> requestOtp(@Valid @RequestBody OtpRequestRequest request,
                                          @AuthenticationPrincipal Object principal,
                                          HttpServletRequest httpRequest) {
        AuthRequestContext context = context(httpRequest);
        OtpPurpose purpose = OtpPurpose.valueOf(request.purpose());
        OtpService.OtpRequestResult result = otpService.requestOtp(
                request.email(), purpose, otpOwner(purpose, request.email(), principal), request.locale(), context);
        return Map.of(
                "otpExpiresAt", result.expiresAt().toString(),
                "canResendInSeconds", result.ttlSeconds(),
                "maskedEmail", maskEmail(request.email()));
    }

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

    @Operation(operationId = "postAuthLogin", summary = "Dang nhap email + mat khau")
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest,
                                     HttpServletResponse httpResponse) {
        AuthRequestContext context = context(httpRequest);
        LoginService.LoginResult result = loginService.login(
                request.email(), request.password(),
                request.rememberMe() == null || request.rememberMe(), context);

        if (mfaService.loginRequiresTotp(result.userId(), result.roles())) {
            // Chua mo phien truoc khi qua TOTP — phien that mo o A10/A11.
            sessionGateway.beginPendingMfa(result.userId(), result.email().value(), result.roles(),
                    request.rememberMe() == null || request.rememberMe());
            mfaService.logCurrentTotpForDev(result.userId(), result.email().value());
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

    @Operation(operationId = "postAuthPasswordResetRequest", summary = "Yeu cau dat lai mat khau")
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequestRequest request,
                                     HttpServletRequest httpRequest) {
        passwordResetService.request(request.email(), context(httpRequest));
    }

    @Operation(operationId = "postAuthPasswordResetConfirm", summary = "Dat mat khau moi")
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request,
                                     HttpServletRequest httpRequest) {
        passwordResetService.confirm(request.token(), request.newPassword(), context(httpRequest));
    }

    @Operation(operationId = "postAuthTotpVerify", summary = "Buoc 2 dang nhap — xac thuc TOTP")
    @PostMapping("/totp/verify")
    public Map<String, Object> verifyTotp(@Valid @RequestBody TotpVerifyRequest request,
                                          @AuthenticationPrincipal Object principal,
                                          HttpServletRequest httpRequest) {
        Optional<UUID> pending = sessionGateway.pendingMfaUserId();
        if (pending.isPresent()) {
            loginService.requireSessionAllowed(pending.get());
            mfaService.verifyForLogin(pending.get(), request.code(), context(httpRequest));
            sessionGateway.completePendingMfa();
            return Map.of("mfaLevel", "TOTP");
        }
        UUID userId = requireUserId(principal);
        mfaService.verifyForLogin(userId, request.code(), context(httpRequest));
        sessionGateway.markMfaTotpVerified();
        return Map.of("mfaLevel", "TOTP");
    }

    @Operation(operationId = "postAuthTotpRecovery", summary = "Dung ma khoi phuc")
    @PostMapping("/totp/recovery")
    public Map<String, Object> totpRecovery(@Valid @RequestBody TotpRecoveryRequest request,
                                            @AuthenticationPrincipal Object principal,
                                            HttpServletRequest httpRequest) {
        Optional<UUID> pending = sessionGateway.pendingMfaUserId();
        UUID userId = pending.isPresent() ? pending.get() : requireUserId(principal);
        if (pending.isPresent()) {
            loginService.requireSessionAllowed(userId);
        }
        MfaService.RecoveryResult result = mfaService.verifyRecovery(
                userId, request.recoveryCode(), context(httpRequest));
        if (pending.isPresent()) {
            sessionGateway.completePendingMfa();
        } else {
            sessionGateway.markMfaTotpVerified();
        }
        return Map.of(
                "mfaLevel", "TOTP",
                "enrollmentRequired", result.enrollmentRequired(),
                "recoveryCodesRemaining", result.recoveryCodesRemaining());
    }

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

    @Operation(operationId = "deleteAuthSession", summary = "Thu hoi mot phien")
    @DeleteMapping("/sessions/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeSession(@PathVariable UUID sessionId,
                              @AuthenticationPrincipal Object principal,
                              HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        sessionService.revokeSession(userId, sessionId, context(httpRequest));
    }

    @Operation(operationId = "postAuthSessionsRevokeAll", summary = "Dang xuat moi thiet bi")
    @PostMapping("/sessions/revoke-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeAllSessions(@AuthenticationPrincipal Object principal,
                                  HttpServletRequest httpRequest) {
        UUID userId = requireUserId(principal);
        sessionService.revokeOtherSessions(userId, context(httpRequest));
    }


    private static List<RegistrationConsentEvent.ConsentGrant> mapConsents(
            List<RegisterRequest.ConsentGrant> consents) {
        if (consents == null) {
            return List.of();
        }
        return consents.stream()
                .map(c -> new RegistrationConsentEvent.ConsentGrant(c.purposeCode(), c.granted()))
                .toList();
    }

    private static final Set<OtpPurpose> ANONYMOUS_OTP_PURPOSES =
            Set.of(OtpPurpose.REGISTER_VERIFY, OtpPurpose.PASSWORD_RESET);

    private static final Set<OtpPurpose> SESSION_OTP_PURPOSES = Set.of(
            OtpPurpose.LOGIN_STEPUP, OtpPurpose.DSAR_VERIFY,
            OtpPurpose.ACCOUNT_DELETE_CONFIRM, OtpPurpose.DATA_EXPORT_CONFIRM);

    /** A4 cong khai: chi hai purpose an danh; purpose step-up gan voi user + email cua phien; EMAIL_CHANGE chi qua B7. */
    private UUID otpOwner(OtpPurpose purpose, String email, Object principal) {
        if (ANONYMOUS_OTP_PURPOSES.contains(purpose)) {
            return null;
        }
        if (!SESSION_OTP_PURPOSES.contains(purpose)) {
            throw new com.catcheck.shared.error.BusinessRuleException(IdentityErrorCode.VALIDATION_FAILED);
        }
        if (!(principal instanceof com.catcheck.identity.application.AuthPrincipal auth)) {
            throw new com.catcheck.shared.error.BusinessRuleException(IdentityErrorCode.UNAUTHENTICATED);
        }
        if (email == null || !email.trim().equalsIgnoreCase(profileService.currentEmail(auth.userId()))) {
            throw new com.catcheck.shared.error.BusinessRuleException(IdentityErrorCode.FORBIDDEN);
        }
        return auth.userId();
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
