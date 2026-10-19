package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AdminUserQueryPort;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** H1 (lockedUntil), H2, M4, M5 — audit opencode-bug-found.md. */
class IdentityAuthHardeningTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthRequestContext CONTEXT =
            AuthRequestContext.of("request-1", "203.0.113.10", "test-agent");
    private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID ADMIN = UUID.fromString("018f0000-0000-7000-8000-000000000009");

    /* ---------- M5 ---------- */

    @Test
    void wrongPasswordWhileTemporarilyLockedDoesNotExtendTheLock() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccount locked = account(UserStatus.ACTIVE, 7, NOW.plusSeconds(600));
        when(accounts.findByEmail(any())).thenReturn(Optional.of(locked));
        when(accounts.findById(USER)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> loginService(accounts).login("person@example.com", "wrong", false, CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.INVALID_CREDENTIALS);
        verify(accounts, never()).updateLoginState(any(), any(), anyInt(), any(), any());
    }

    @Test
    void wrongPasswordOnLockedStatusAccountIsNotCounted() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccount locked = account(UserStatus.LOCKED, 3, null);
        when(accounts.findByEmail(any())).thenReturn(Optional.of(locked));
        when(accounts.findById(USER)).thenReturn(Optional.of(locked));

        assertThatThrownBy(() -> loginService(accounts).login("person@example.com", "wrong", false, CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
        verify(accounts, never()).updateLoginState(any(), any(), anyInt(), any(), any());
    }

    @Test
    void wrongPasswordOnUnlockedAccountStillCounts() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserAccount open = account(UserStatus.ACTIVE, 0, NOW.minusSeconds(1));
        when(accounts.findByEmail(any())).thenReturn(Optional.of(open));
        when(accounts.findById(USER)).thenReturn(Optional.of(open));

        assertThatThrownBy(() -> loginService(accounts).login("person@example.com", "wrong", false, CONTEXT))
                .isInstanceOf(BusinessRuleException.class);
        verify(accounts).updateLoginState(eq(USER), eq(UserStatus.ACTIVE), eq(1), any(), any());
    }

    /* ---------- H1: OAuth honours lockedUntil ---------- */

    @Test
    void oauthCannotBypassTheTemporaryLockout() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findByProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), USER, IdentityProvider.GOOGLE,
                        "google-sub", "person@example.com", true, null, NOW, NOW, NOW, NOW)));
        when(accounts.findById(USER)).thenReturn(Optional.of(account(UserStatus.ACTIVE, 5, NOW.plusSeconds(60))));

        OAuthService service = new OAuthService(accounts, identities, mock(UserRoleRepository.class),
                mock(AuditLogService.class), mock(org.springframework.context.ApplicationEventPublisher.class),
                CLOCK);

        assertThatThrownBy(() -> service.findOrCreate(Map.of("sub", "google-sub",
                "email", "person@example.com", "email_verified", true), CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_RESTRICTED);
        verify(identities, never()).touchLastUsed(any(), any());
    }

    /* ---------- H2 ---------- */

    @Test
    void adminLockKillsTheRealHttpSessions() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        when(accounts.findById(USER)).thenReturn(Optional.of(account(UserStatus.ACTIVE, 0, null)));
        DeviceSessionRepository devices = mock(DeviceSessionRepository.class);
        AuthenticatedSessionRevoker revoker = mock(AuthenticatedSessionRevoker.class);

        adminService(accounts, mock(AdminUserQueryPort.class), devices, revoker).lock(USER, adminContext());

        verify(devices).revokeAllByUserId(USER, NOW, SessionRevokeReason.ADMIN_LOCK);
        verify(revoker).revokeAllByUserId(USER);
    }

    @Test
    void adminRevokeAllKillsTheRealHttpSessions() {
        AdminUserQueryPort query = mock(AdminUserQueryPort.class);
        when(query.findById(USER)).thenReturn(Optional.of(mock(AdminUserSummary.class)));
        AuthenticatedSessionRevoker revoker = mock(AuthenticatedSessionRevoker.class);

        adminService(mock(UserAccountRepository.class), query, mock(DeviceSessionRepository.class), revoker)
                .revokeAllSessions(USER, adminContext());

        verify(revoker).revokeAllByUserId(USER);
    }

    /* ---------- M4 ---------- */

    @Test
    void ownerlessStepUpTicketIsRejectedNotA500() {
        OtpService otp = mock(OtpService.class);
        when(otp.consumeTicket("ticket", OtpPurpose.LOGIN_STEPUP)).thenReturn(stepUpChallenge(null));
        AuthenticatedSessionGateway gateway = mock(AuthenticatedSessionGateway.class);
        when(gateway.currentEmail()).thenReturn(Optional.of("person@example.com"));

        assertThatThrownBy(() -> sessionService(gateway, otp).reauthenticate(USER, "EMAIL_OTP", "ticket", CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.REAUTH_FAILED);
        verify(gateway, never()).markReauthenticated(anyInt());
    }

    @Test
    void stepUpTicketOwnedByTheCallerOpensTheWindow() {
        OtpService otp = mock(OtpService.class);
        when(otp.consumeTicket("ticket", OtpPurpose.LOGIN_STEPUP)).thenReturn(stepUpChallenge(USER));
        AuthenticatedSessionGateway gateway = mock(AuthenticatedSessionGateway.class);
        when(gateway.currentEmail()).thenReturn(Optional.of("person@example.com"));

        sessionService(gateway, otp).reauthenticate(USER, "EMAIL_OTP", "ticket", CONTEXT);

        verify(gateway).markReauthenticated(300);
    }

    /* ---------- helpers ---------- */

    private static LoginService loginService(UserAccountRepository accounts) {
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findLocal(USER)).thenReturn(Optional.of(new UserIdentity(UUID.randomUUID(), USER,
                IdentityProvider.LOCAL, "person@example.com", "person@example.com", true, "hash",
                NOW, NOW, NOW, NOW)));
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.matches(any(), any())).thenReturn(false);
        return new LoginService(accounts, identities, mock(UserRoleRepository.class), passwords,
                policy(), allowingLimiter(), mock(AuthenticatedSessionRevoker.class),
                mock(AuditLogService.class), CLOCK);
    }

    private static AdminUserService adminService(UserAccountRepository accounts, AdminUserQueryPort query,
                                                 DeviceSessionRepository devices,
                                                 AuthenticatedSessionRevoker revoker) {
        return new AdminUserService(query, accounts, mock(UserRoleRepository.class), devices, revoker,
                mock(MfaTotpRepository.class), mock(PiiCipher.class), mock(AuditLogService.class), CLOCK);
    }

    private static AdminUserService.AdminActionContext adminContext() {
        return new AdminUserService.AdminActionContext(ADMIN, "ADMIN_SUPER", "test", "r", "203.0.113.1", "ua");
    }

    private static SessionService sessionService(AuthenticatedSessionGateway gateway, OtpService otp) {
        return new SessionService(mock(DeviceSessionRepository.class), mock(UserIdentityRepository.class),
                mock(UserAccountRepository.class), gateway, mock(AuthenticatedSessionRevoker.class), otp,
                mock(PasswordHasher.class), new PasswordPolicy(Set.of()), policy(), allowingLimiter(),
                mock(AuditLogService.class), CLOCK);
    }

    private static EmailOtpChallenge stepUpChallenge(UUID owner) {
        return new EmailOtpChallenge(UUID.randomUUID(), owner, "person@example.com", "hash", 1,
                OtpPurpose.LOGIN_STEPUP, NOW.plusSeconds(60), 0, 5, null, CONTEXT.ipAddress(), NOW,
                "ticket-hash", NOW.plusSeconds(60), NOW);
    }

    private static RateLimiter allowingLimiter() {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.rejectIfLimited(any(), any())).thenReturn(null);
        return limiter;
    }

    private static AuthPolicy policy() {
        return new AuthPolicy(Duration.ofMinutes(5), 5, Duration.ofMinutes(10), 6,
                12, 8, 72, 3, Duration.ofMinutes(1), 5, Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofHours(12), 5);
    }

    private static UserAccount account(UserStatus status, int failedLogins, Instant lockedUntil) {
        return new UserAccount(USER, EmailAddress.of("person@example.com"), NOW, "Person", null,
                null, null, null, AppLocale.VI, "Asia/Ho_Chi_Minh", status,
                OnboardingStatus.ACCOUNT_ONLY, failedLogins, lockedUntil, null, null, null, null, null, null,
                null, null, null, NOW, NOW);
    }
}
