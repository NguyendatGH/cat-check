package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AppLocale;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.OtpCodeGenerator;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.OtpTicket;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.port.EmailOtpRepository;
import com.catcheck.identity.domain.port.OtpCodeHasher;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.RateLimitedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final EmailOtpRepository otpRepository;
    private final UserAccountRepository accountRepository;
    private final OtpCodeHasher codeHasher;
    private final OtpCodeGenerator codeGenerator;
    private final AuthPolicy policy;
    private final RateLimiter rateLimiter;
    private final NotificationGateway notificationGateway;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final boolean devConsoleEnabled;

    public OtpService(EmailOtpRepository otpRepository,
                      UserAccountRepository accountRepository,
                      OtpCodeHasher codeHasher,
                      OtpCodeGenerator codeGenerator,
                      AuthPolicy policy,
                      RateLimiter rateLimiter,
                      NotificationGateway notificationGateway,
                      AuditLogService auditLogService,
                      Clock clock,
                      @Value("${catcheck.dev.log-otp:false}") boolean devConsoleEnabled) {
        this.otpRepository = otpRepository;
        this.accountRepository = accountRepository;
        this.codeHasher = codeHasher;
        this.codeGenerator = codeGenerator;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.notificationGateway = notificationGateway;
        this.auditLogService = auditLogService;
        this.clock = clock;
        this.devConsoleEnabled = devConsoleEnabled;
    }

    @Transactional
    public OtpRequestResult requestOtp(String email, OtpPurpose purpose, UUID userId,
                                       String requestedLocale, AuthRequestContext context) {
        Instant now = clock.instant();
        String subject = email + '|' + purpose.name();

        require(RateLimitRule.OTP_REQUEST_BURST, subject);
        require(RateLimitRule.OTP_REQUEST_HOURLY, subject);
        require(RateLimitRule.OTP_REQUEST_DAILY, subject);
        require(RateLimitRule.OTP_REQUEST_BY_IP, context.ipAddress());

        otpRepository.supersedeActive(email, purpose, now);

        int pepperVersion = codeHasher.currentPepperVersion();
        String code = codeGenerator.generate();
        Instant expiresAt = now.plus(policy.otpTtl());
        UUID challengeId = UUID.randomUUID();

        otpRepository.insert(new EmailOtpChallenge(
                challengeId,
                userId,
                email,
                codeHasher.hash(pepperVersion, purpose, email, code),
                pepperVersion,
                purpose,
                expiresAt,
                0,
                policy.otpMaxAttempts(),
                null,
                context.ipAddress(),
                null,
                null,
                null,
                now));

        deliver(email, purpose, code, challengeId, userId, requestedLocale);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_OTP_REQUESTED")
                .meta("purpose", purpose.name())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new OtpRequestResult(expiresAt, policy.otpTtl().toSeconds());
    }

    @Transactional
    public OtpTicket verifyOtp(String email, OtpPurpose purpose, String submittedCode,
                               AuthRequestContext context) {
        Instant now = clock.instant();
        require(RateLimitRule.OTP_VERIFY, email + '|' + purpose.name());

        EmailOtpChallenge challenge = otpRepository.findActive(email, purpose)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_EXPIRED));

        if (!challenge.isUsableAt(now)) {
            auditDenied(challenge, purpose, challenge.hasAttemptsLeft() ? "expired" : "attempts_exhausted",
                    context);
            throw new BusinessRuleException(
                    challenge.hasAttemptsLeft()
                            ? IdentityErrorCode.OTP_EXPIRED
                            : IdentityErrorCode.OTP_LOCKED);
        }

        String candidate = codeHasher.hashForStoredVersion(
                challenge.pepperVersion(), purpose, email, submittedCode);

        if (!MessageDigest.isEqual(
                candidate.getBytes(StandardCharsets.UTF_8),
                challenge.codeHash().getBytes(StandardCharsets.UTF_8))) {
            int attemptsLeft = Math.max(
                    0, challenge.maxAttempts() - otpRepository.incrementAttempt(challenge.id()));
            auditDenied(challenge, purpose, "wrong_code", context);
            if (attemptsLeft == 0) {
                otpRepository.supersedeActive(email, purpose, now);
                throw new RateLimitedException(IdentityErrorCode.OTP_LOCKED, 60L);
            }
            throw new BusinessRuleException(IdentityErrorCode.OTP_INVALID, attemptsLeft);
        }

        Instant ticketExpiresAt = now.plus(policy.otpTicketTtl());
        OtpTicket ticket = OtpTicket.create(ticketExpiresAt);
        otpRepository.markVerified(challenge.id(), now, OtpTicket.hashOf(ticket.rawValue()),
                ticketExpiresAt);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(challenge.userId())
                .action("AUTH_OTP_VERIFIED")
                .meta("purpose", purpose.name())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return ticket;
    }

    @Transactional
    public EmailOtpChallenge consumeTicket(String rawTicket, OtpPurpose purpose) {
        Instant now = clock.instant();
        EmailOtpChallenge challenge = otpRepository.findByTicketHash(OtpTicket.hashOf(rawTicket))
                .filter(candidate -> candidate.purpose() == purpose)
                .filter(candidate -> candidate.isTicketValidAt(now))
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));
        otpRepository.markConsumed(challenge.id(), now);
        return challenge;
    }

    private void deliver(String email, OtpPurpose purpose, String code, UUID challengeId, UUID userId,
                         String requestedLocale) {
        if (devConsoleEnabled) {
            log.warn("[DEV-OTP] purpose={} email={} code={}", purpose, maskEmail(email), code);
        }
        notificationGateway.enqueueTransactionalEmail(new TransactionalEmailRequest(
                email,
                templateCodeFor(purpose),
                localeOf(email, userId, requestedLocale),
                Map.of(
                        "code", code,
                        "purpose", purpose.name(),
                        "expiresInMinutes", String.valueOf(policy.otpTtl().toMinutes())),
                "AUTH_OTP:" + challengeId));
    }

    // app_user.locale của người nhận; gửi lại OTP không có userId thì tra theo email. Chưa có tài
    // khoản (OTP trước khi đăng ký) dùng ngôn ngữ client gửi, sai hoặc thiếu thì về vi.
    private String localeOf(String email, UUID userId, String requestedLocale) {
        Optional<UserAccount> account = userId != null
                ? accountRepository.findById(userId)
                : Optional.ofNullable(EmailAddress.parseOrNull(email)).flatMap(accountRepository::findByEmail);
        return account.map(UserAccount::locale)
                .orElseGet(() -> AppLocale.fromCodeOrDefault(requestedLocale)).code();
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + (at < 0 ? "" : email.substring(at));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String templateCodeFor(OtpPurpose purpose) {
        return purpose == OtpPurpose.PASSWORD_RESET ? "AUTH_OTP_RESET_PASSWORD" : "AUTH_OTP_REGISTER";
    }

    private void auditDenied(EmailOtpChallenge challenge, OtpPurpose purpose, String reason,
                             AuthRequestContext context) {
        auditLogService.record(AuditEvent.builder()
                .subjectUser(challenge.userId())
                .action("AUTH_OTP_VERIFY")
                .denied()
                .meta("reason", reason)
                .meta("purpose", purpose.name())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    private void require(RateLimitRule rule, String subject) {
        RateLimiter.RetryAfter retryAfter = rateLimiter.rejectIfLimited(rule, subject);
        if (retryAfter != null) {
            throw new RateLimitedException(IdentityErrorCode.RATE_LIMITED,
                    retryAfter.headerSeconds(), rule.name());
        }
    }

    public record OtpRequestResult(Instant expiresAt, long ttlSeconds) {
    }
}
