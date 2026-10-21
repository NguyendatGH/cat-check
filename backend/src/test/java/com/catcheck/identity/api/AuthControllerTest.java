package com.catcheck.identity.api;

import com.catcheck.identity.api.dto.LoginRequest;
import com.catcheck.identity.api.dto.OtpRequestRequest;
import com.catcheck.identity.api.dto.TotpVerifyRequest;
import com.catcheck.identity.application.AuthPrincipal;
import com.catcheck.identity.application.LoginService;
import com.catcheck.identity.application.MfaService;
import com.catcheck.identity.application.OtpService;
import com.catcheck.identity.application.PasswordResetService;
import com.catcheck.identity.application.ProfileService;
import com.catcheck.identity.application.RegistrationService;
import com.catcheck.identity.application.SessionService;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** C1 (khong mo phien truoc buoc TOTP) va M3/M4 (chu + muc dich OTP o A4). */
class AuthControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000001");

    private LoginService loginService;
    private MfaService mfaService;
    private OtpService otpService;
    private AuthenticatedSessionGateway gateway;
    private ProfileService profileService;
    private AuthController controller;

    @BeforeEach
    void setUp() {
        loginService = mock(LoginService.class);
        mfaService = mock(MfaService.class);
        otpService = mock(OtpService.class);
        gateway = mock(AuthenticatedSessionGateway.class);
        profileService = mock(ProfileService.class);
        when(profileService.currentEmail(USER)).thenReturn("person@example.com");
        when(gateway.pendingMfaUserId()).thenReturn(Optional.empty());
        when(otpService.requestOtp(any(), any(), any(), any(), any()))
                .thenReturn(new OtpService.OtpRequestResult(NOW.plusSeconds(300), 300));
        controller = new AuthController(mock(RegistrationService.class), otpService, loginService,
                mock(PasswordResetService.class), mfaService, mock(SessionService.class),
                profileService, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /* ---------- C1 ---------- */

    @Test
    void passwordLoginWithActiveTotpDoesNotEstablishASession() {
        when(loginService.login(any(), any(), anyBoolean(), any())).thenReturn(new LoginService.LoginResult(
                USER, EmailAddress.of("person@example.com"), "Person", Set.of("USER")));
        when(mfaService.loginRequiresTotp(eq(USER), any())).thenReturn(true);

        Map<String, Object> body = controller.login(new LoginRequest("person@example.com", "pw", true),
                new MockHttpServletRequest(), new MockHttpServletResponse());

        assertThat(body).containsEntry("mfaRequired", true);
        verify(gateway).beginPendingMfa(USER, "person@example.com", Set.of("USER"), true);
        verify(gateway, never()).establish(any(), any(), any(), anyBoolean());
    }

    @Test
    void totpVerifyCompletesThePendingLogin() {
        when(gateway.pendingMfaUserId()).thenReturn(Optional.of(USER));

        controller.verifyTotp(new TotpVerifyRequest("123456"), null, new MockHttpServletRequest());

        verify(mfaService).verifyForLogin(eq(USER), eq("123456"), any());
        verify(gateway).completePendingMfa();
    }

    @Test
    void pendingLoginOfAnAccountLockedMeanwhileIsNotCompleted() {
        when(gateway.pendingMfaUserId()).thenReturn(Optional.of(USER));
        org.mockito.Mockito.doThrow(new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED))
                .when(loginService).requireSessionAllowed(USER);

        assertThatThrownBy(() -> controller.verifyTotp(new TotpVerifyRequest("123456"), null,
                new MockHttpServletRequest())).isInstanceOf(BusinessRuleException.class);
        verify(gateway, never()).completePendingMfa();
    }

    @Test
    void stepUpCodeFollowsTheAccountsCurrentEmailNotTheOneAtLogin() {
        when(profileService.currentEmail(USER)).thenReturn("changed@example.com");

        assertThatThrownBy(() -> controller.requestOtp(
                new OtpRequestRequest("person@example.com", "LOGIN_STEPUP", null, null), principal(),
                new MockHttpServletRequest())).isInstanceOf(BusinessRuleException.class);
        controller.requestOtp(new OtpRequestRequest("changed@example.com", "LOGIN_STEPUP", null, null), principal(),
                new MockHttpServletRequest());

        verify(otpService).requestOtp(eq("changed@example.com"), eq(OtpPurpose.LOGIN_STEPUP), eq(USER), any(), any());
    }

    @Test
    void wrongTotpCodeLeavesTheSessionUnauthenticated() {
        when(gateway.pendingMfaUserId()).thenReturn(Optional.of(USER));
        when(mfaService.verifyForLogin(eq(USER), eq("000000"), any()))
                .thenThrow(new BusinessRuleException(IdentityErrorCode.TOTP_INVALID));

        assertThatThrownBy(() -> controller.verifyTotp(new TotpVerifyRequest("000000"), null,
                new MockHttpServletRequest())).isInstanceOf(BusinessRuleException.class);
        verify(gateway, never()).completePendingMfa();
        verify(gateway, never()).establish(any(), any(), any(), anyBoolean());
    }

    @Test
    void totpVerifyWithoutPendingLoginOrSessionIsUnauthenticated() {
        assertThatThrownBy(() -> controller.verifyTotp(new TotpVerifyRequest("123456"), null,
                new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.UNAUTHENTICATED);
    }

    /* ---------- M3 / M4 ---------- */

    @Test
    void anonymousCallerCanStillRequestRegistrationAndResetCodes() {
        controller.requestOtp(new OtpRequestRequest("a@example.com", "REGISTER_VERIFY", null, "en"), null,
                new MockHttpServletRequest());
        controller.requestOtp(new OtpRequestRequest("a@example.com", "PASSWORD_RESET", null, null), null,
                new MockHttpServletRequest());

        verify(otpService).requestOtp(eq("a@example.com"), eq(OtpPurpose.REGISTER_VERIFY), isNull(), eq("en"), any());
        verify(otpService).requestOtp(eq("a@example.com"), eq(OtpPurpose.PASSWORD_RESET), isNull(), any(), any());
    }

    @Test
    void anonymousCallerCannotRequestStepUpCodes() {
        assertThatThrownBy(() -> controller.requestOtp(new OtpRequestRequest("victim@example.com", "LOGIN_STEPUP", null, null),
                null, new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.UNAUTHENTICATED);
        verify(otpService, never()).requestOtp(any(), any(), any(), any(), any());
    }

    @Test
    void emailChangeCodesOnlyGoThroughTheAccountEndpoint() {
        assertThatThrownBy(() -> controller.requestOtp(new OtpRequestRequest("new@example.com", "EMAIL_CHANGE", null, null),
                principal(), new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class);
        verify(otpService, never()).requestOtp(any(), any(), any(), any(), any());
    }

    @Test
    void stepUpCodeIsBoundToTheSignedInUser() {
        controller.requestOtp(new OtpRequestRequest("Person@Example.com", "LOGIN_STEPUP", null, null), principal(),
                new MockHttpServletRequest());

        verify(otpService).requestOtp(eq("Person@Example.com"), eq(OtpPurpose.LOGIN_STEPUP), eq(USER), any(), any());
    }

    @Test
    void stepUpCodeCannotBeSentToSomeoneElsesEmail() {
        assertThatThrownBy(() -> controller.requestOtp(new OtpRequestRequest("victim@example.com", "LOGIN_STEPUP", null, null),
                principal(), new MockHttpServletRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.FORBIDDEN);
        verify(otpService, never()).requestOtp(any(), any(), any(), any(), any());
    }

    private static AuthPrincipal principal() {
        return AuthPrincipal.of(USER, "person@example.com", Set.of("USER"), NOW);
    }
}
