package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Doi email (p8 B7 + B8, p11 §11.2.1).
 *
 * <p>OTP {@code EMAIL_CHANGE} gui toi <b>dia chi moi</b>; thong bao gui toi dia chi
 * <b>cu</b> (p8 B7: {@code POST /account/email-change/request}). Xac nhan bang
 * {@code otp_ticket} — email la khoa dang nhap nen khong the tu tay.</p>
 */
@Service
public class EmailChangeService {

    private final UserAccountRepository accountRepository;
    private final OtpService otpService;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public EmailChangeService(UserAccountRepository accountRepository,
                             OtpService otpService,
                             RateLimiter rateLimiter,
                             AuditLogService auditLogService,
                             Clock clock) {
        this.accountRepository = accountRepository;
        this.otpService = otpService;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** B7 — gui OTP {@code EMAIL_CHANGE} toi dia chi moi. */
    @Transactional
    public OtpService.OtpRequestResult requestChange(UUID userId, String rawNewEmail,
                                                     AuthRequestContext context) {
        RateLimiter.RetryAfter retryAfter =
                rateLimiter.rejectIfLimited(RateLimitRule.OTP_REQUEST_BURST, rawNewEmail);
        if (retryAfter != null) {
            throw new RateLimitedException(IdentityErrorCode.RATE_LIMITED,
                    retryAfter.headerSeconds(), "EMAIL");
        }

        EmailAddress newEmail = EmailAddress.of(rawNewEmail);
        var account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.UNAUTHENTICATED));

        if (account.email().equals(newEmail)) {
            throw new BusinessRuleException(IdentityErrorCode.EMAIL_CHANGE_SAME_ADDRESS);
        }
        if (accountRepository.existsByEmail(newEmail)) {
            throw new BusinessRuleException(IdentityErrorCode.EMAIL_ALREADY_USED);
        }

        OtpService.OtpRequestResult result =
                otpService.requestOtp(newEmail.value(), OtpPurpose.EMAIL_CHANGE, userId, context);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_EMAIL_CHANGE_REQUESTED")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return result;
    }

    /** B8 — xac nhan doi email bang {@code otp_ticket}. */
    @Transactional
    public void confirmChange(UUID userId, String rawOtpTicket, AuthRequestContext context) {
        var challenge = otpService.consumeTicket(rawOtpTicket, OtpPurpose.EMAIL_CHANGE);
        if (!challenge.userId().equals(userId)) {
            throw new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID);
        }

        Instant now = clock.instant();
        var account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.OTP_TICKET_INVALID));

        accountRepository.updateEmail(account.id(), challenge.email(), now);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_EMAIL_CHANGED")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }
}
