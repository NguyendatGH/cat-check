package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.EmailOtpChallenge;
import com.catcheck.identity.domain.OtpCodeGenerator;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.OtpTicket;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.port.EmailOtpRepository;
import com.catcheck.identity.domain.port.OtpCodeHasher;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.notification.api.NotificationGateway;
import com.catcheck.notification.api.TransactionalEmailRequest;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sinh, gui va kiem tra ma OTP email (p11 §11.2.1).
 *
 * <p>Khong bao gio luu ma tho: DB chi co
 * {@code code_hash = HMAC-SHA256(pepper, purpose‖email‖code)}. Pepper nam ngoai DB nen
 * ro DB cung khong khoi phuc duoc ma (research §2.4).</p>
 *
 * <p><b>Ba lop bao ve lap o nhau</b>, khong the thay the nhau:</p>
 * <ol>
 *   <li>TTL 5 phut — gioi han cua <i>cu so ma</i>.</li>
 *   <li>5 lan thu — gioi han cua <i>cua so doan</i> (challenge).</li>
 *   <li>Rate limit — gioi han cua <i>luong request</i>, chan truoc khi sinh ma va
 *       truoc khi hash.</li>
 * </ol>
 */
@Service
public class OtpService {

    private final EmailOtpRepository otpRepository;
    private final OtpCodeHasher codeHasher;
    private final OtpCodeGenerator codeGenerator;
    private final AuthPolicy policy;
    private final RateLimiter rateLimiter;
    private final NotificationGateway notificationGateway;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public OtpService(EmailOtpRepository otpRepository,
                      OtpCodeHasher codeHasher,
                      OtpCodeGenerator codeGenerator,
                      AuthPolicy policy,
                      RateLimiter rateLimiter,
                      NotificationGateway notificationGateway,
                      AuditLogService auditLogService,
                      Clock clock) {
        this.otpRepository = otpRepository;
        this.codeHasher = codeHasher;
        this.codeGenerator = codeGenerator;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.notificationGateway = notificationGateway;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /**
     * Gui ma moi (p8 A4 {@code POST /auth/otp/request}).
     *
     * <p>Challenge dang active cho cung {@code (email, purpose)} bi huy truoc. Day la p11
     * §11.2.1 "chi giu challenge moi nhat": giu lai ma cu nghia la nguoi dung da nhan
     * email moi nhung lai nhap ma cu — DoS do chinh nguoi goi.</p>
     */
    @Transactional
    public OtpRequestResult requestOtp(String email, OtpPurpose purpose, UUID userId,
                                       AuthRequestContext context) {
        Instant now = clock.instant();
        String subject = email + '|' + purpose.name();

        require(RateLimitRule.OTP_REQUEST_BURST, subject);
        require(RateLimitRule.OTP_REQUEST_HOURLY, subject);
        require(RateLimitRule.OTP_REQUEST_DAILY, subject);

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

        deliver(email, purpose, code, challengeId);

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

    /**
     * Kiem tra ma (p8 A5 {@code POST /auth/otp/verify}).
     *
     * <p>Tra ve {@code otp_ticket} de request sau khong phai gui lai ma. Ticket van phai
     * con hieu luc va chi dung mot lan — xem {@link #consumeTicket}.</p>
     */
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

        // So sanh bang hash cua <b>pepper version da luu</b>: doi pepper khong duoc lam vo
        // cac challenge dang chay (p11 §11.2.1 yeu cau luu pepper_version).
        String candidate = codeHasher.hashForStoredVersion(
                challenge.pepperVersion(), purpose, email, submittedCode);

        if (!candidate.equals(challenge.codeHash())) {
            int attemptsLeft = Math.max(
                    0, challenge.maxAttempts() - otpRepository.incrementAttempt(challenge.id()));
            auditDenied(challenge, purpose, "wrong_code", context);
            if (attemptsLeft == 0) {
                otpRepository.supersedeActive(email, purpose, now);
                throw new RateLimitedException(IdentityErrorCode.OTP_LOCKED, 60L);
            }
            throw new BusinessRuleException(IdentityErrorCode.OTP_INVALID, attemptsLeft);
        }

        // Ma dung -> gan verified_at va sinh ticket. Ma 6 chu so het tac dung tu day.
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

    /**
     * Tieu ticket va tra ve challenge da xac minh (buoc dau cho A3 co {@code otpTicket},
     * A8/A9 va cac luong doi email cua nhom B).
     *
     * @throws BusinessRuleException {@code OTP_TICKET_INVALID} — <b>mot</b> thong bao cho
     *         het han / da dung / khong ton tai / sai muc dich, de khong lo ra may do
     */
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

    /**
     * Xep email OTP vao {@code email_outbox} (p12 §12.4: API chi INSERT roi tra {@code 202},
     * KHONG goi SMTP trong request thread). {@code SendEmailOutboxJob} day di sau, toi da 30 giay.
     *
     * <p><b>Doi so voi ban truoc:</b> truoc day goi thang {@code EmailSender.send(...)} — dong
     * bo, nuot loi, khong retry. Hop dong API khong doi ({@code POST /auth/otp/request} van tra
     * {@code 202 Accepted} kem {@code otpExpiresAt}); cai doi la mot lan SMTP that bai gio co
     * backoff 1m/5m/30m/2h/12h thay vi mat han.</p>
     *
     * <p><b>Cho p12 mo ta khong khop p11 — da chon p11:</b> p12 §12.4 yeu cau
     * {@code email_outbox.payload} "KHONG chua ma OTP tho — job doc ma tu {@code email_otp} luc
     * render". Khong lam duoc: p11 §11.2.1 (part SO HUU mien ma hoa) chot {@code email_otp} chi
     * luu {@code code_hash} HMAC-SHA256 co pepper, mot chieu, co y de "ma tho khong ton tai o
     * dau". Mot trong hai yeu cau phai nhuong; da giu p11 (bao mat o trang thai nghi) va dat ma
     * vao {@code payload}, keo theo hai bu tru: {@code JdbcEmailOutboxRepository.markSent} xoa
     * {@code payload} ngay sau khi gui, va ma van het han sau 5 phut. <b>Can p12 §12.4 sua lai
     * cau nay.</b></p>
     *
     * <p><b>Thieu ma template cho 5 trong 7 {@code OtpPurpose}:</b> p12 §12.2.2 chi dinh nghia
     * {@code AUTH_OTP_REGISTER} va {@code AUTH_OTP_RESET_PASSWORD}; nhom
     * {@code AUTH_SECURITY_*} (§12.2.4) khong co ma nao cho OTP cua {@code EMAIL_CHANGE},
     * {@code LOGIN_STEPUP}, {@code DSAR_VERIFY}, {@code ACCOUNT_DELETE_CONFIRM},
     * {@code DATA_EXPORT_CONFIRM}. Tam dung {@code AUTH_OTP_REGISTER} cho nam truong hop do
     * (noi dung "Ma xac thuc cua ban la ..." van dung ngu canh) va <b>khong</b> tu dat ma moi —
     * p12 §12.2.2 ghi ro Part 12 la noi duy nhat dinh nghia gia tri {@code template_code}.</p>
     */
    private void deliver(String email, OtpPurpose purpose, String code, UUID challengeId) {
        notificationGateway.enqueueTransactionalEmail(new TransactionalEmailRequest(
                email,
                templateCodeFor(purpose),
                "vi",
                Map.of(
                        "code", code,
                        "purpose", purpose.name(),
                        "expiresInMinutes", String.valueOf(policy.otpTtl().toMinutes())),
                // Mot challenge = mot email. Challenge moi co id moi nen "gui lai" khong bi
                // uq_email_outbox_dedupe chan.
                "AUTH_OTP:" + challengeId));
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

    /** Ket qua {@link #requestOtp}. */
    public record OtpRequestResult(Instant expiresAt, long ttlSeconds) {
    }
}
