package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.credit.api.BuyerAccountActivatedEvent;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.privacy.spi.RegistrationConsentEvent;
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
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class RegistrationService {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final OtpService otpService;
    private final AuditLogService auditLogService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public RegistrationService(UserAccountRepository accountRepository,
                               UserIdentityRepository identityRepository,
                               UserRoleRepository roleRepository,
                               PasswordHasher passwordHasher,
                               PasswordPolicy passwordPolicy,
                               OtpService otpService,
                               AuditLogService auditLogService,
                               ApplicationEventPublisher eventPublisher,
                               Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.otpService = otpService;
        this.auditLogService = auditLogService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    public record RegistrationResult(
            UUID userId,
            EmailAddress email,
            String fullName,
            boolean emailVerified,
            boolean authenticated) {
    }

    @Transactional
    public RegistrationResult register(String rawEmail,
                                       String rawPassword,
                                       String fullName,
                                       String locale,
                                       String rawOtpTicket,
                                       List<RegistrationConsentEvent.ConsentGrant> consents,
                                       AuthRequestContext context) {
        EmailAddress email = EmailAddress.of(rawEmail);
        Instant now = clock.instant();

        if (rawOtpTicket != null && !rawOtpTicket.isBlank()) {
            return completePreverifiedRegistration(email, rawPassword, locale, rawOtpTicket, now, context);
        }

        if (accountRepository.existsByEmail(email)) {
            auditLogService.record(AuditEvent.builder()
                    .action("AUTH_REGISTER")
                    .denied()
                    .meta("reason", "email_already_registered")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .build());
            throw new ConflictException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }

        requireAcceptablePassword(rawPassword);

        UUID userId = UUID.randomUUID();
        accountRepository.insert(new UserAccount(
                userId, email, null, fullName, null, null, null, null,
                parseLocale(locale), "Asia/Ho_Chi_Minh",
                UserStatus.PENDING_VERIFICATION, OnboardingStatus.ACCOUNT_ONLY,
                0, null, null, null, null, null, null, null, null, null, null,
                now, now));
        identityRepository.insert(new UserIdentity(
                UUID.randomUUID(), userId, IdentityProvider.LOCAL, email.value(), email.value(),
                false, passwordHasher.hash(rawPassword), now, null, now, now));
        roleRepository.grant(userId, UserRole.USER, null);

        if (consents != null && !consents.isEmpty()) {
            eventPublisher.publishEvent(new RegistrationConsentEvent(
                    userId, consents, parseLocale(locale).code(), context.ipAddress(),
                    context.userAgent(), context.requestId(), now));
        }

        otpService.requestOtp(email.value(), OtpPurpose.REGISTER_VERIFY, userId, context);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_REGISTER")
                .meta("onboarding", OnboardingStatus.ACCOUNT_ONLY.name())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new RegistrationResult(userId, email, fullName, false, false);
    }

    private RegistrationResult completePreverifiedRegistration(EmailAddress email,
                                                              String rawPassword,
                                                              String locale,
                                                              String rawOtpTicket,
                                                              Instant now,
                                                              AuthRequestContext context) {
        EmailOtpChallenge challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.REGISTER_VERIFY);
        UserAccount account = accountRepository.findById(challenge.userId())
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));

        if (account.emailVerifiedAt() != null) {
            throw new ConflictException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        if (!account.email().equals(email)) {
            throw new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID);
        }
        requireAcceptablePassword(rawPassword);

        accountRepository.markEmailVerified(account.id(), now);
        identityRepository.findLocal(account.id())
                .ifPresent(identity -> identityRepository.touchLastUsed(identity.id(), now));
        publishBuyerActivated(account.id(), now);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(account.id())
                .action("AUTH_EMAIL_VERIFIED")
                .meta("via", "register_ticket")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());

        return new RegistrationResult(account.id(), account.email(), account.fullName(), true, true);
    }

    @Transactional
    public RegistrationResult activateWithTicket(String rawOtpTicket, AuthRequestContext context) {
        EmailOtpChallenge challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.REGISTER_VERIFY);
        Instant now = clock.instant();
        UserAccount account = accountRepository.findById(challenge.userId())
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));

        if (account.emailVerifiedAt() == null) {
            accountRepository.markEmailVerified(account.id(), now);
            publishBuyerActivated(account.id(), now);
        }

        auditLogService.record(AuditEvent.builder()
                .subjectUser(account.id())
                .action("AUTH_EMAIL_VERIFIED")
                .meta("via", "otp_verify")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());

        return new RegistrationResult(account.id(), account.email(), account.fullName(), true, true);
    }

    /**
     * Tài khoản email chỉ "dùng được" sau khi xác minh OTP — đó là lúc tặng quà chào mừng, để
     * hạn 5 ngày không trôi khi user chưa xác minh và nhắc hết hạn không gửi tới email chưa xác minh.
     * Chỉ tài khoản người mua thuần (vai trò USER duy nhất); staff không nhận quà.
     */
    private void publishBuyerActivated(UUID userId, Instant now) {
        if (!BuyerAccounts.isBuyerOnly(roleRepository.findByUserId(userId))) {
            return;
        }
        eventPublisher.publishEvent(new BuyerAccountActivatedEvent(
                userId, BuyerAccountActivatedEvent.SignupMethod.EMAIL, now));
    }

    private void requireAcceptablePassword(String rawPassword) {
        PasswordPolicy.Violation violation = passwordPolicy.check(rawPassword);
        if (violation == null) {
            return;
        }
        throw switch (violation) {
            case TOO_SHORT, TOO_LONG -> new BusinessRuleException(IdentityErrorCode.PASSWORD_TOO_WEAK);
            case REPEATED_CHARS, SEQUENTIAL_CHARS, BLOCKED ->
                    new BusinessRuleException(IdentityErrorCode.PASSWORD_BREACHED);
        };
    }

    private static AppLocale parseLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return AppLocale.VI;
        }
        try {
            return AppLocale.fromCode(locale);
        } catch (IllegalArgumentException ex) {
            return AppLocale.VI;
        }
    }
}
