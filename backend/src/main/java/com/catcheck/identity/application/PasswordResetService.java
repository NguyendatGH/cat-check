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

/**
 * Quen mat khau (p3 F3, p8 A8 + A9, p11 §11.3.2).
 *
 * <p><b>Khong bao gio tra loi khi email khong ton tai.</b> A8 luon 200
 * ({@link IdentityErrorCode#PASSWORD_RESET_REQUESTED}) — neu tra 404 cho email la,
 * A8 tro thanh API kiem tra email ton tai. Client khong phan biet hai nhanh, va thoi
 * gian phan hoi phai nhat nhau de khong do duoc may so sanh (p11 §11.1.5).</p>
 *
 * <p>A9 khong nhan mat khau cua client. Nhan {@code otp_ticket} — bang chung OTP da
 * duoc xac minh — roi dat mat khau moi. Cach nay loai duoc ca p11 §11.3.2 yeu cau
 * "token phai la chuoi ngau nhien rieng" ma van giu duoc tien do cua OTP (da verify
 * email trong 5 phut chu khong phai vong xac minh thu hai).</p>
 */
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

    /**
     * A8 — <b>luon</b> 200. Trong ca hai nhanh deu ghi audit, nhung chi nhanh co tai
     * khoan moi gui OTP; nhanh khong co tai khoan ghi {@code denied} de doi chieu duoc
     * nguoi dung report "toi khong nhan duoc email".
     */
    @Transactional
    public void request(String rawEmail, AuthRequestContext context) {
        String subject = rawEmail == null ? "" : rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
        requireRateLimit(subject);

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
        otpService.requestOtp(user.email().value(), OtpPurpose.PASSWORD_RESET, user.id(), context);
        // password_reset chi ghi khi co ticket that, khong ghi "co yeu cau" — bang nay
        // la bang chung, khong phai log trang thai.
        auditLogService.record(AuditEvent.builder()
                .subjectUser(user.id())
                .action("AUTH_PASSWORD_RESET_REQUEST")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());
    }

    /** A9 — dat mat khau moi bang {@code otp_ticket}. Tra ve {@code userId} moi xac nhan. */
    @Transactional
    public UUID confirm(String rawOtpTicket, String newPassword, AuthRequestContext context) {
        requireRateLimit("confirm");

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

        // Dong hoan thanh bang chung, khong phai token sinh rieng: ticket da bi tie o
        // OtpService, dong nay chi luu hash de audit/doi chieu va chan ghi lai.
        String ticketHash = OtpTicket.hashOf(rawOtpTicket);
        passwordResetRepository.insert(userId, ticketHash, challenge.ticketExpiresAt(),
                context.ipAddress());

        // p11 §11.3.2: doi mat khau phai thu hoi moi phien khac. Neu khong, mat khau cu
        // da bi lo ra khoe truc tiep van dung duoc den khi nguoi dung doi lai lan nua.
        int revoked = sessionRevoker.revokeAllByUserId(userId);

        // Reset so lan sai + go khoa: dang reset mat khau la bang chung chu so huu,
        // khong vi pham nua n nen khong phai khoa tai khoan o day.
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

    private void requireRateLimit(String subject) {
        RateLimiter.RetryAfter byEmail =
                rateLimiter.rejectIfLimited(RateLimitRule.PASSWORD_RESET_BY_EMAIL, subject);
        if (byEmail != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byEmail.headerSeconds(), "EMAIL");
        }
        RateLimiter.RetryAfter byIp =
                rateLimiter.rejectIfLimited(RateLimitRule.PASSWORD_RESET_BY_IP, null);
        if (byIp != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byIp.headerSeconds(), "IP");
        }
    }

    /** Ly do thu hoi phien khi doi mat khau — dung chung cho A9 va B6. */
    public static SessionRevokeReason passwordChangedReason() {
        return SessionRevokeReason.PASSWORD_CHANGED;
    }

    /** Han dung cua OTP mat khau = han cua ticket, de A9 khong phai tra OTP rieng. */
    public java.time.Duration resetTicketTtl() {
        return policy.otpTicketTtl();
    }
}
