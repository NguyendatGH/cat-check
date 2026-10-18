package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.MfaTotp;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.OtpCodeGenerator;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.TotpStatus;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.domain.port.EmailOtpRepository;
import com.catcheck.identity.domain.port.MfaRecoveryCodeRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.OtpCodeHasher;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.PasswordResetRepository;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.RecoveryCodeHasher;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.identity.infrastructure.ratelimit.Bucket4jRateLimiter;
import com.catcheck.media.api.ImageStorage;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.CatCheckException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import com.catcheck.shared.security.MfaLevel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentitySecurityLogicReviewTest {

    private static final Instant NOW = Instant.parse("2026-10-10T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthRequestContext CONTEXT =
            AuthRequestContext.of("request-1", "203.0.113.10", "test-agent");
    private static final UUID USER_A = UUID.fromString("018f0000-0000-7000-8000-000000000001");
    private static final UUID USER_B = UUID.fromString("018f0000-0000-7000-8000-000000000002");

    @Test
    void loginConsumesTheCallerIpRateLimitBucket() {
        RateLimiter limiter = allowingLimiter();
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.dummyHash()).thenReturn("dummy");
        when(passwords.matches(any(), any())).thenReturn(false);

        LoginService service = new LoginService(mock(UserAccountRepository.class),
                mock(UserIdentityRepository.class), mock(UserRoleRepository.class), passwords,
                policy(), limiter, mock(AuthenticatedSessionRevoker.class), audit(), CLOCK);

        assertThatThrownBy(() -> service.login("nobody@example.com", "password", false, CONTEXT))
                .isInstanceOf(BusinessRuleException.class);

        verify(limiter).rejectIfLimited(RateLimitRule.LOGIN_BY_IP, CONTEXT.ipAddress());
    }

    @Test
    void loginIpBucketReallyBlocksOnePublicIpAcrossManyEmails() {
        LoginService service = loginServiceWith(new Bucket4jRateLimiter());
        AuthRequestContext attacker = AuthRequestContext.of("r", "203.0.113.10", "ua");

        for (int i = 0; i < 10; i++) {
            String email = "victim" + i + "@example.com";
            assertThatThrownBy(() -> service.login(email, "guess", false, attacker))
                    .isInstanceOf(BusinessRuleException.class);
        }

        assertThatThrownBy(() -> service.login("victim10@example.com", "guess", false, attacker))
                .isInstanceOf(RateLimitedException.class)
                .extracting(error -> ((RateLimitedException) error).errorCode())
                .isEqualTo(IdentityErrorCode.RATE_LIMITED);
    }

    @Test
    void proxyHopAddressIsNotOneSharedLoginBucketForEveryone() {
        LoginService service = loginServiceWith(new Bucket4jRateLimiter());

        for (String proxy : List.of("127.0.0.1", "172.18.0.5", "::1")) {
            AuthRequestContext viaProxy = AuthRequestContext.of("r", proxy, "ua");
            for (int i = 0; i < 12; i++) {
                String email = "user" + i + "@example.com";
                assertThatThrownBy(() -> service.login(email, "wrong", false, viaProxy))
                        .isInstanceOf(BusinessRuleException.class)
                        .isNotInstanceOf(RateLimitedException.class);
            }
        }
    }

    @Test
    void otpRequestConsumesTheCallerIpRateLimitBucket() {
        RateLimiter limiter = allowingLimiter();
        OtpCodeHasher hasher = mock(OtpCodeHasher.class);
        when(hasher.currentPepperVersion()).thenReturn(1);
        when(hasher.hash(any(Integer.class), any(), any(), any())).thenReturn("hash");

        OtpService service = new OtpService(mock(EmailOtpRepository.class), mock(UserAccountRepository.class), hasher,
                new OtpCodeGenerator(), policy(), limiter, mock(NotificationGateway.class), audit(), CLOCK, false);

        service.requestOtp("person@example.com", OtpPurpose.REGISTER_VERIFY, null, null, CONTEXT);

        verify(limiter).rejectIfLimited(RateLimitRule.OTP_REQUEST_BY_IP, CONTEXT.ipAddress());
    }

    @Test
    void otpEmailUsesTheStoredLocaleThenTheRequestedOneThenTheDefault() {
        OtpCodeHasher hasher = mock(OtpCodeHasher.class);
        when(hasher.currentPepperVersion()).thenReturn(1);
        when(hasher.hash(any(Integer.class), any(), any(), any())).thenReturn("hash");
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        when(accounts.findById(USER_A)).thenReturn(Optional.of(account(USER_A, UserStatus.ACTIVE, AppLocale.EN)));
        when(accounts.findByEmail(EmailAddress.of("person@example.com")))
                .thenReturn(Optional.of(account(USER_A, UserStatus.PENDING_VERIFICATION, AppLocale.EN)));
        NotificationGateway gateway = mock(NotificationGateway.class);
        OtpService service = new OtpService(mock(EmailOtpRepository.class), accounts, hasher,
                new OtpCodeGenerator(), policy(), allowingLimiter(), gateway, audit(), CLOCK, false);

        service.requestOtp("person@example.com", OtpPurpose.REGISTER_VERIFY, USER_A, null, CONTEXT);
        service.requestOtp("person@example.com", OtpPurpose.REGISTER_VERIFY, null, null, CONTEXT);
        service.requestOtp("nobody@example.com", OtpPurpose.REGISTER_VERIFY, null, null, CONTEXT);
        service.requestOtp("person@example.com", OtpPurpose.REGISTER_VERIFY, USER_A, "vi", CONTEXT);
        service.requestOtp("nobody@example.com", OtpPurpose.REGISTER_VERIFY, null, "en", CONTEXT);
        service.requestOtp("nobody@example.com", OtpPurpose.REGISTER_VERIFY, null, "fr", CONTEXT);

        ArgumentCaptor<TransactionalEmailRequest> sent = ArgumentCaptor.forClass(TransactionalEmailRequest.class);
        verify(gateway, times(6)).enqueueTransactionalEmail(sent.capture());
        assertThat(sent.getAllValues()).extracting(TransactionalEmailRequest::locale)
                .containsExactly("en", "en", "vi", "en", "en", "vi");
    }

    @Test
    void wrongOtpHashIsRejectedAndCountsAnAttempt() {
        EmailOtpRepository challenges = mock(EmailOtpRepository.class);
        OtpCodeHasher hasher = mock(OtpCodeHasher.class);
        when(hasher.hashForStoredVersion(any(Integer.class), any(), any(), any())).thenReturn("different-hash");
        EmailOtpChallenge challenge = new EmailOtpChallenge(UUID.randomUUID(), USER_A,
                "person@example.com", "stored-hash", 1, OtpPurpose.REGISTER_VERIFY,
                NOW.plusSeconds(60), 0, 5, null, CONTEXT.ipAddress(), null, null, null, NOW);
        when(challenges.findActive("person@example.com", OtpPurpose.REGISTER_VERIFY))
                .thenReturn(Optional.of(challenge));
        when(challenges.incrementAttempt(challenge.id())).thenReturn(1);

        OtpService service = new OtpService(challenges, mock(UserAccountRepository.class), hasher, new OtpCodeGenerator(),
                policy(), allowingLimiter(), mock(NotificationGateway.class), audit(), CLOCK, false);

        assertThatThrownBy(() -> service.verifyOtp("person@example.com", OtpPurpose.REGISTER_VERIFY,
                "123456", CONTEXT)).isInstanceOf(BusinessRuleException.class);

        verify(challenges).incrementAttempt(challenge.id());
        verify(challenges, never()).markVerified(any(), any(), any(), any());
    }

    @Test
    void passwordResetConfirmDoesNotUseOneSharedEmailBucket() {
        RateLimiter limiter = allowingLimiter();
        OtpService otp = mock(OtpService.class);
        when(otp.consumeTicket("ticket-a", OtpPurpose.PASSWORD_RESET))
                .thenReturn(ticketChallenge(USER_A, "a@example.com"));
        when(otp.consumeTicket("ticket-b", OtpPurpose.PASSWORD_RESET))
                .thenReturn(ticketChallenge(USER_B, "b@example.com"));
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findLocal(any())).thenAnswer(invocation -> Optional.of(localIdentity(invocation.getArgument(0))));
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.hash(any())).thenReturn("new-hash");
        PasswordResetRepository resets = mock(PasswordResetRepository.class);
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        when(accounts.findById(any())).thenAnswer(invocation ->
                Optional.of(account(invocation.getArgument(0), UserStatus.ACTIVE)));

        PasswordResetService service = new PasswordResetService(accounts, identities,
                resets, passwords, new PasswordPolicy(Set.of()), policy(), otp, limiter,
                mock(AuthenticatedSessionRevoker.class), audit(), CLOCK);

        service.confirm("ticket-a", "FreshPassword9", CONTEXT);
        service.confirm("ticket-b", "FreshPassword9", CONTEXT);

        verify(limiter, never()).rejectIfLimited(eq(RateLimitRule.PASSWORD_RESET_BY_EMAIL), any());
        verify(limiter, org.mockito.Mockito.times(2))
                .rejectIfLimited(RateLimitRule.PASSWORD_RESET_BY_IP, CONTEXT.ipAddress());
    }

    @Test
    void googleOnlyProfileDoesNotClaimToHaveAPassword() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        UserAccount account = account(USER_A, UserStatus.ACTIVE);
        when(accounts.findById(USER_A)).thenReturn(Optional.of(account));
        when(identities.findByUserId(USER_A)).thenReturn(List.of(
                new UserIdentity(UUID.randomUUID(), USER_A, IdentityProvider.LOCAL, "person@example.com",
                        "person@example.com", true, null, NOW, null, NOW, NOW),
                new UserIdentity(UUID.randomUUID(), USER_A, IdentityProvider.GOOGLE, "google-sub",
                        "person@example.com", true, null, NOW, NOW, NOW, NOW)));

        ProfileService service = new ProfileService(accounts, identities, null, audit(), CLOCK,
                "target/avatars-test", mock(ImageStorage.class));

        ProfileService.ProfileView view = service.profile(USER_A);

        assertThat(view.hasPassword()).isFalse();
        assertThat(view.identities()).containsExactly("LOCAL", "GOOGLE");
    }

    @Test
    void oauthCannotBypassAnAccountRestriction() {
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        UserIdentity google = new UserIdentity(UUID.randomUUID(), USER_A, IdentityProvider.GOOGLE,
                "google-sub", "person@example.com", true, null, NOW, NOW, NOW, NOW);
        when(identities.findByProviderSubject(IdentityProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.of(google));
        when(accounts.findById(USER_A)).thenReturn(Optional.of(account(USER_A, UserStatus.RESTRICTED)));

        OAuthService service = new OAuthService(accounts, identities, mock(UserRoleRepository.class), audit(),
                mock(ApplicationEventPublisher.class), CLOCK);

        assertThatThrownBy(() -> service.findOrCreate(Map.of("sub", "google-sub",
                "email", "person@example.com", "email_verified", true), CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_RESTRICTED);
        verify(identities, never()).touchLastUsed(any(), any());
    }

    @Test
    void lockedTotpEnrollmentCannotContinueGuessing() {
        MfaTotpRepository totps = mock(MfaTotpRepository.class);
        MfaTotp lockedPending = new MfaTotp(USER_A, new byte[] {1}, TotpStatus.PENDING,
                "SHA1", 6, 30, null, 5, NOW.plusSeconds(60), NOW.plusSeconds(600), null,
                1, null, null, NOW, NOW);
        when(totps.findByUserId(USER_A)).thenReturn(Optional.of(lockedPending));

        MfaService service = new MfaService(totps, mock(MfaRecoveryCodeRepository.class),
                mock(UserRoleRepository.class), mock(com.catcheck.identity.api.PiiCipher.class),
                mock(RecoveryCodeHasher.class), allowingLimiter(), audit(), CLOCK, false);

        assertThatThrownBy(() -> service.confirm(USER_A, "123456", CONTEXT))
                .isInstanceOf(RateLimitedException.class)
                .extracting(error -> ((RateLimitedException) error).errorCode())
                .isEqualTo(IdentityErrorCode.TOTP_LOCKED);
    }

    @Test
    void passwordOnlySessionCannotReplaceOrMintCodesForAnActiveTotp() {
        MfaTotpRepository totps = mock(MfaTotpRepository.class);
        MfaRecoveryCodeRepository codes = mock(MfaRecoveryCodeRepository.class);
        when(totps.findByUserId(USER_A)).thenReturn(Optional.of(activeTotp()));
        MfaService service = mfaService(totps, codes);

        assertThatThrownBy(() -> service.regenerateRecoveryCodes(USER_A, MfaLevel.NONE, CONTEXT))
                .isInstanceOf(PermissionDeniedException.class)
                .extracting(error -> ((CatCheckException) error).errorCode())
                .isEqualTo(IdentityErrorCode.TOTP_REQUIRED);
        assertThatThrownBy(() -> service.init(USER_A, "person@example.com", MfaLevel.NONE, CONTEXT))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> service.delete(USER_A, MfaLevel.NONE, CONTEXT))
                .isInstanceOf(PermissionDeniedException.class);

        verify(codes, never()).insertBatch(any(), any());
        verify(codes, never()).deleteByUserId(any());
        verify(totps, never()).delete(any());
    }

    @Test
    void totpVerifiedSessionStillManagesItsTotp() {
        MfaTotpRepository totps = mock(MfaTotpRepository.class);
        MfaRecoveryCodeRepository codes = mock(MfaRecoveryCodeRepository.class);
        when(totps.findByUserId(USER_A)).thenReturn(Optional.of(activeTotp()));
        MfaService service = mfaService(totps, codes);

        assertThat(service.regenerateRecoveryCodes(USER_A, MfaLevel.TOTP, CONTEXT).recoveryCodes())
                .hasSize(MfaService.RECOVERY_CODE_BATCH_SIZE);
    }

    @Test
    void passwordChangeKeepsTheCurrentSession() {
        UUID current = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        String currentHash = "current";
        String otherHash = "other";
        AuthenticatedSessionGateway gateway = mock(AuthenticatedSessionGateway.class);
        when(gateway.currentSessionIdHash()).thenReturn(Optional.of(currentHash));
        AuthenticatedSessionRevoker revoker = mock(AuthenticatedSessionRevoker.class);
        when(revoker.activeSessionIdsByUserId(USER_A)).thenReturn(Set.of(currentHash, otherHash));
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findLocal(USER_A)).thenReturn(Optional.of(localIdentity(USER_A)));
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.matches("CurrentPassword9", "old-hash")).thenReturn(true);
        when(passwords.matches("FreshPassword9", "old-hash")).thenReturn(false);
        when(passwords.hash("FreshPassword9")).thenReturn("new-hash");

        SessionService service = new SessionService(mock(com.catcheck.identity.domain.port.DeviceSessionRepository.class),
                identities, mock(UserAccountRepository.class), gateway, revoker, mock(OtpService.class), passwords,
                new PasswordPolicy(Set.of()), policy(), allowingLimiter(), audit(), CLOCK);

        service.changePassword(USER_A, "CurrentPassword9", "FreshPassword9", CONTEXT);

        verify(revoker).revokeBySessionId(otherHash);
        verify(revoker, never()).revokeBySessionId(currentHash);
        verify(revoker, never()).revokeAllByUserId(USER_A);
    }

    @Test
    void logOutOtherDevicesKeepsTheDeviceInUse() {
        AuthenticatedSessionGateway gateway = mock(AuthenticatedSessionGateway.class);
        when(gateway.currentSessionIdHash()).thenReturn(Optional.of("current"));
        AuthenticatedSessionRevoker revoker = mock(AuthenticatedSessionRevoker.class);
        when(revoker.activeSessionIdsByUserId(USER_A)).thenReturn(Set.of("current", "laptop", "phone"));

        int revoked = sessionService(gateway, revoker, mock(DeviceSessionRepository.class))
                .revokeOtherSessions(USER_A, CONTEXT);

        assertThat(revoked).isEqualTo(2);
        verify(revoker).revokeBySessionId("laptop");
        verify(revoker).revokeBySessionId("phone");
        verify(revoker, never()).revokeBySessionId("current");
        verify(revoker, never()).revokeAllByUserId(any());
    }

    @Test
    void sessionListHidesDeviceRowsWhoseRealSessionIsGone() {
        DeviceSessionRepository devices = mock(DeviceSessionRepository.class);
        when(devices.findActiveByUserId(eq(USER_A), any())).thenReturn(List.of(
                deviceRow("live-hash"), deviceRow("timed-out-hash")));
        AuthenticatedSessionRevoker revoker = mock(AuthenticatedSessionRevoker.class);
        when(revoker.activeSessionIdsByUserId(USER_A)).thenReturn(Set.of("live-hash"));
        AuthenticatedSessionGateway gateway = mock(AuthenticatedSessionGateway.class);
        when(gateway.currentSessionIdHash()).thenReturn(Optional.of("live-hash"));

        List<SessionService.SessionView> sessions = sessionService(gateway, revoker, devices).listSessions(USER_A);

        assertThat(sessions).singleElement().extracting(SessionService.SessionView::current).isEqualTo(true);
    }

    @Test
    void emailChangeTicketWithoutOwnerIsRejectedNotA500() {
        OtpService otp = mock(OtpService.class);
        when(otp.consumeTicket("ticket", OtpPurpose.EMAIL_CHANGE)).thenReturn(new EmailOtpChallenge(
                UUID.randomUUID(), null, "new@example.com", "hash", 1, OtpPurpose.EMAIL_CHANGE,
                NOW.plusSeconds(60), 0, 5, null, CONTEXT.ipAddress(), NOW, "ticket-hash",
                NOW.plusSeconds(60), NOW));
        UserAccountRepository accounts = mock(UserAccountRepository.class);
        EmailChangeService service = new EmailChangeService(accounts, otp, allowingLimiter(), audit(), CLOCK);

        assertThatThrownBy(() -> service.confirmChange(USER_A, "ticket", CONTEXT))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(error -> ((BusinessRuleException) error).errorCode())
                .isEqualTo(IdentityErrorCode.OTP_TICKET_INVALID);
        verify(accounts, never()).updateEmail(any(), any(), any());
    }

    private static SessionService sessionService(AuthenticatedSessionGateway gateway,
                                                 AuthenticatedSessionRevoker revoker,
                                                 DeviceSessionRepository devices) {
        return new SessionService(devices, mock(UserIdentityRepository.class), mock(UserAccountRepository.class),
                gateway, revoker, mock(OtpService.class), mock(PasswordHasher.class),
                new PasswordPolicy(Set.of()), policy(), allowingLimiter(), audit(), CLOCK);
    }

    private static DeviceSession deviceRow(String sessionIdHash) {
        return new DeviceSession(UUID.randomUUID(), USER_A, sessionIdHash, "Trinh duyet", "ua", null,
                true, NOW, NOW, NOW.plus(Duration.ofDays(30)), null, null);
    }

    private static MfaTotp activeTotp() {
        return new MfaTotp(USER_A, new byte[] {1}, TotpStatus.ACTIVE, "SHA1", 6, 30, 1L, 0, null,
                null, NOW.minusSeconds(3600), 1, null, null, NOW, NOW);
    }

    private static MfaService mfaService(MfaTotpRepository totps, MfaRecoveryCodeRepository codes) {
        RecoveryCodeHasher hasher = mock(RecoveryCodeHasher.class);
        when(hasher.currentPepperVersion()).thenReturn(1);
        when(hasher.hash(any(Integer.class), any())).thenReturn("hash");
        return new MfaService(totps, codes, mock(UserRoleRepository.class),
                mock(com.catcheck.identity.api.PiiCipher.class), hasher, allowingLimiter(), audit(), CLOCK, false);
    }

    private static LoginService loginServiceWith(RateLimiter limiter) {
        PasswordHasher passwords = mock(PasswordHasher.class);
        when(passwords.dummyHash()).thenReturn("dummy");
        when(passwords.matches(any(), any())).thenReturn(false);
        return new LoginService(mock(UserAccountRepository.class), mock(UserIdentityRepository.class),
                mock(UserRoleRepository.class), passwords, policy(), limiter,
                mock(AuthenticatedSessionRevoker.class), audit(), CLOCK);
    }

    private static RateLimiter allowingLimiter() {
        RateLimiter limiter = mock(RateLimiter.class);
        when(limiter.rejectIfLimited(any(), any())).thenReturn(null);
        return limiter;
    }

    private static AuditLogService audit() {
        return mock(AuditLogService.class);
    }

    private static AuthPolicy policy() {
        return new AuthPolicy(Duration.ofMinutes(5), 5, Duration.ofMinutes(10), 6,
                12, 8, 72, 3, Duration.ofMinutes(1), 5, Duration.ofMinutes(15),
                Duration.ofDays(30), Duration.ofHours(12), 5);
    }

    private static EmailOtpChallenge ticketChallenge(UUID userId, String email) {
        return new EmailOtpChallenge(UUID.randomUUID(), userId, email, "hash", 1,
                OtpPurpose.PASSWORD_RESET, NOW.plusSeconds(60), 0, 5, null, CONTEXT.ipAddress(),
                NOW, "ticket-hash", NOW.plusSeconds(60), NOW);
    }

    private static UserIdentity localIdentity(UUID userId) {
        return new UserIdentity(UUID.randomUUID(), userId, IdentityProvider.LOCAL,
                "person@example.com", "person@example.com", true, "old-hash", NOW, NOW, NOW, NOW);
    }

    private static UserAccount account(UUID userId, UserStatus status) {
        return account(userId, status, AppLocale.VI);
    }

    private static UserAccount account(UUID userId, UserStatus status, AppLocale locale) {
        return new UserAccount(userId, EmailAddress.of("person@example.com"), NOW, "Person", null,
                null, null, null, locale, "Asia/Ho_Chi_Minh", status,
                OnboardingStatus.ACCOUNT_ONLY, 0, null, null, null, null, null, null, null,
                null, null, null, NOW, NOW);
    }
}
