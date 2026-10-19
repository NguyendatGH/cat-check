package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.OtpTicket;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.PasswordResetRepository;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class PasswordResetService {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuthPolicy policy;
    private final OtpService otpService;
    private final RateLimiter rateLimiter;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public PasswordResetService(UserAccountRepository accountRepository,
                                UserIdentityRepository identityRepository,
                                PasswordResetRepository passwordResetRepository,
                                PasswordHasher passwordHasher,
                                PasswordPolicy passwordPolicy,
                                AuthPolicy policy,
                                OtpService otpService,
                                RateLimiter rateLimiter,
                                AuthenticatedSessionRevoker sessionRevoker,
                                AuditLogService auditLogService,
                                Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.policy = policy;
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
        this.sessionRevoker = sessionRevoker;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public void request(String rawEmail, AuthRequestContext context) {
        String subject = rawEmail == null ? "" : rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        requireEmailRateLimit(subject);
        requireIpRateLimit(context);

        EmailAddress email = EmailAddress.parseOrNull(rawEmail);
        var account = email == null ? java.util.Optional.<UserAccount>empty()
                : accountRepository.findByEmail(email);

        if (account.isEmpty()) {
            auditLogService.record(AuditEvent.builder()
                    .action("AUTH_PASSWORD_RESET_REQUEST")
                    .denied()
                    .meta("reason", "unknown_account")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .build());
            return;
        }

        UserAccount user = account.get();
        otpService.requestOtp(user.email().value(), OtpPurpose.PASSWORD_RESET, user.id(), null, context);
        auditLogService.record(AuditEvent.builder()
                .subjectUser(user.id())
                .action("AUTH_PASSWORD_RESET_REQUEST")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());
    }

    @Transactional
    public UUID confirm(String rawOtpTicket, String newPassword, AuthRequestContext context) {
        requireIpRateLimit(context);

        var challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.PASSWORD_RESET);
        UUID userId = challenge.userId();
        requireAcceptablePassword(newPassword);

        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));
        UserIdentity identity = identityRepository.findLocal(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.REAUTH_METHOD_UNAVAILABLE));

        Instant now = clock.instant();
        accountRepository.updatePasswordHash(userId, passwordHasher.hash(newPassword));
        accountRepository.updatePasswordTimestamps(userId, now, null);

        String ticketHash = OtpTicket.hashOf(rawOtpTicket);
        passwordResetRepository.insert(userId, ticketHash, challenge.ticketExpiresAt(),
                context.ipAddress());

        int revoked = sessionRevoker.revokeAllByUserId(userId);

        if (account.status() == UserStatus.LOCKED) {
            accountRepository.updateLoginState(userId, UserStatus.ACTIVE, 0, null, now);
        } else {
            accountRepository.updateLoginState(userId, account.status(), 0, null, now);
        }

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_PASSWORD_RESET_COMPLETE")
                .meta("sessions_revoked", revoked)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return userId;
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

    private void requireEmailRateLimit(String subject) {
        RateLimiter.RetryAfter byEmail =
                rateLimiter.rejectIfLimited(RateLimitRule.PASSWORD_RESET_BY_EMAIL, subject);
        if (byEmail != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byEmail.headerSeconds(), "EMAIL");
        }
    }

    private void requireIpRateLimit(AuthRequestContext context) {
        RateLimiter.RetryAfter byIp =
                rateLimiter.rejectIfLimited(RateLimitRule.PASSWORD_RESET_BY_IP, context.ipAddress());
        if (byIp != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byIp.headerSeconds(), "IP");
        }
    }

    public static SessionRevokeReason passwordChangedReason() {
        return SessionRevokeReason.PASSWORD_CHANGED;
    }

    public java.time.Duration resetTicketTtl() {
        return policy.otpTicketTtl();
    }
}
