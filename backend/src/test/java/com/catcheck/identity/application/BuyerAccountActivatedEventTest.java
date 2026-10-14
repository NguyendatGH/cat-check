package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditLogService;
import com.catcheck.credit.api.BuyerAccountActivatedEvent;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.IdentityProvider;
import com.catcheck.identity.domain.OnboardingStatus;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Quà chào mừng: identity phát BuyerAccountActivatedEvent đúng chỗ, và không phát cho staff. */
class BuyerAccountActivatedEventTest {

    private static final Instant NOW = Instant.parse("2026-05-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AuthRequestContext CONTEXT =
            AuthRequestContext.of("request-1", "203.0.113.10", "test-agent");
    private static final UUID USER = UUID.fromString("018f0000-0000-7000-8000-0000000000aa");

    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final UserIdentityRepository identities = mock(UserIdentityRepository.class);
    private final UserRoleRepository roles = mock(UserRoleRepository.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final OtpService otpService = mock(OtpService.class);

    @Test
    void googleSignUpOfANewAccountPublishesTheEventOnce() {
        when(identities.findByProviderSubject(IdentityProvider.GOOGLE, "sub-new")).thenReturn(Optional.empty());
        when(accounts.findByEmail(any())).thenReturn(Optional.empty());
        when(accounts.findById(any())).thenAnswer(inv -> Optional.of(account(inv.getArgument(0), null)));
        when(roles.findByUserId(any())).thenReturn(List.of(UserRole.USER));

        OAuthService.OAuthResult result = oauth().findOrCreate(claims("sub-new"), CONTEXT);

        ArgumentCaptor<BuyerAccountActivatedEvent> captor = ArgumentCaptor.forClass(BuyerAccountActivatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(result.newlyCreated()).isTrue();
        assertThat(captor.getValue().userId()).isEqualTo(result.userId());
        assertThat(captor.getValue().method()).isEqualTo(BuyerAccountActivatedEvent.SignupMethod.GOOGLE);
        assertThat(captor.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void googleLoginOfAnExistingAccountDoesNotPublish() {
        UserIdentity google = new UserIdentity(UUID.randomUUID(), USER, IdentityProvider.GOOGLE,
                "sub-old", "person@example.com", true, null, NOW, NOW, NOW, NOW);
        when(identities.findByProviderSubject(IdentityProvider.GOOGLE, "sub-old")).thenReturn(Optional.of(google));
        when(accounts.findById(USER)).thenReturn(Optional.of(account(USER, NOW)));
        when(roles.findByUserId(USER)).thenReturn(List.of(UserRole.USER));

        oauth().findOrCreate(claims("sub-old"), CONTEXT);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void emailVerificationOfABuyerPublishesTheEvent() {
        stubTicketFor(USER, null);
        when(roles.findByUserId(USER)).thenReturn(List.of(UserRole.USER));

        registration().activateWithTicket("ticket", CONTEXT);

        ArgumentCaptor<BuyerAccountActivatedEvent> captor = ArgumentCaptor.forClass(BuyerAccountActivatedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER);
        assertThat(captor.getValue().method()).isEqualTo(BuyerAccountActivatedEvent.SignupMethod.EMAIL);
    }

    @Test
    void reVerifyingAnAlreadyVerifiedAccountDoesNotPublish() {
        stubTicketFor(USER, NOW.minusSeconds(60));
        when(roles.findByUserId(USER)).thenReturn(List.of(UserRole.USER));

        registration().activateWithTicket("ticket", CONTEXT);

        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void staffAccountsNeverGetTheWelcomeEvent() {
        stubTicketFor(USER, null);
        when(roles.findByUserId(USER)).thenReturn(List.of(UserRole.USER, UserRole.ADMIN_CATALOG));

        registration().activateWithTicket("ticket", CONTEXT);

        verify(accounts).markEmailVerified(eq(USER), eq(NOW));
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void buyerOnlyMeansExactlyTheUserRole() {
        assertThat(BuyerAccounts.isBuyerOnly(List.of(UserRole.USER))).isTrue();
        assertThat(BuyerAccounts.isBuyerOnly(java.util.Set.of("USER"))).isTrue();
        assertThat(BuyerAccounts.isBuyerOnly(List.of())).isFalse();
        assertThat(BuyerAccounts.isBuyerOnly(List.of(UserRole.ADMIN_SUPER))).isFalse();
        assertThat(BuyerAccounts.isBuyerOnly(java.util.Set.of("USER", "MODERATOR"))).isFalse();
    }

    private void stubTicketFor(UUID userId, Instant emailVerifiedAt) {
        EmailOtpChallenge challenge = new EmailOtpChallenge(UUID.randomUUID(), userId, "person@example.com",
                "hash", 1, OtpPurpose.REGISTER_VERIFY, NOW.plusSeconds(600), 0, 5, null, null,
                NOW, "ticket-hash", NOW.plusSeconds(600), NOW);
        when(otpService.consumeTicket("ticket", OtpPurpose.REGISTER_VERIFY)).thenReturn(challenge);
        when(accounts.findById(userId)).thenReturn(Optional.of(account(userId, emailVerifiedAt)));
    }

    private OAuthService oauth() {
        return new OAuthService(accounts, identities, roles, mock(AuditLogService.class), publisher, CLOCK);
    }

    private RegistrationService registration() {
        return new RegistrationService(accounts, identities, roles, mock(PasswordHasher.class),
                mock(PasswordPolicy.class), otpService, mock(AuditLogService.class), publisher, CLOCK);
    }

    private static Map<String, Object> claims(String sub) {
        return Map.of("sub", sub, "email", "person@example.com", "email_verified", true, "name", "Person");
    }

    private static UserAccount account(UUID userId, Instant emailVerifiedAt) {
        return new UserAccount(userId, EmailAddress.of("person@example.com"), emailVerifiedAt, "Person", null,
                null, null, null, AppLocale.VI, "Asia/Ho_Chi_Minh", UserStatus.ACTIVE,
                OnboardingStatus.ACCOUNT_ONLY, 0, null, null, null, null, null, null, null,
                null, null, null, NOW, NOW);
    }
}
