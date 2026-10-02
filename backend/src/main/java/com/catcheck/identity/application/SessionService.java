package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.identity.domain.OtpPurpose;
import com.catcheck.identity.domain.PasswordPolicy;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.port.AuthenticatedSessionGateway;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.NotFoundException;
import com.catcheck.shared.error.PermissionDeniedException;
import com.catcheck.shared.error.RateLimitedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Quan ly phien (p8 A7 + A13) va doi mat khau (p8 B6).
 *
 * <p>Phien HTTP that su nam o Spring Session; {@code user_device_session} la ban sao de
 * hien thi. {@link #listSessions} doc ban sao, {@link #revokeSession} ghi thu hoi vao
 * ca hai — xem {@code docs/handovers/A1.md} ve phan cai dat Spring Session.</p>
 */
@Service
public class SessionService {

    private final DeviceSessionRepository sessionRepository;
    private final UserIdentityRepository identityRepository;
    private final UserAccountRepository accountRepository;
    private final AuthenticatedSessionGateway sessionGateway;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final OtpService otpService;
    private final PasswordHasher passwordHasher;
    private final PasswordPolicy passwordPolicy;
    private final AuthPolicy policy;
    private final RateLimiter rateLimiter;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public SessionService(DeviceSessionRepository sessionRepository,
                          UserIdentityRepository identityRepository,
                          UserAccountRepository accountRepository,
                          AuthenticatedSessionGateway sessionGateway,
                          AuthenticatedSessionRevoker sessionRevoker,
                          OtpService otpService,
                          PasswordHasher passwordHasher,
                          PasswordPolicy passwordPolicy,
                          AuthPolicy policy,
                          RateLimiter rateLimiter,
                          AuditLogService auditLogService,
                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.identityRepository = identityRepository;
        this.accountRepository = accountRepository;
        this.sessionGateway = sessionGateway;
        this.sessionRevoker = sessionRevoker;
        this.otpService = otpService;
        this.passwordHasher = passwordHasher;
        this.passwordPolicy = passwordPolicy;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    /** A13 {@code GET /auth/sessions}. */
    public List<SessionView> listSessions(UUID userId) {
        Instant now = clock.instant();
        String currentHash = sessionGateway.currentSessionIdHash().orElse(null);
        return sessionRepository.findActiveByUserId(userId, now).stream()
                .map(session -> SessionView.of(session, currentHash))
                .toList();
    }

    /**
     * A7 {@code POST /auth/sessions/{id}/revoke} — thu hoi <b>mot</b> phien khac.
     *
     * <p>Phien hien tai khong thu hoi qua API: client muon logout thi goi
     * {@code POST /auth/logout}. Cho phep thu hoi chinh no se tao trai nghiem dang
     * nhap xong bi logout ngay.</p>
     */
    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, AuthRequestContext context) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(candidate -> candidate.userId().equals(userId))
                // p11 §11.5.3 + p8 §8.2.4: tai nguyen nguoi khac tra 404 chu khong phai 403.
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.SESSION_NOT_FOUND));

        if (sessionGateway.currentSessionIdHash()
                .map(session::isCurrent)
                .orElse(Boolean.FALSE)) {
            throw new BusinessRuleException(IdentityErrorCode.SESSION_NOT_FOUND);
        }
        if (!sessionRepository.revoke(sessionId, clock.instant(), SessionRevokeReason.USER_REVOKE_ONE)) {
            throw new NotFoundException(IdentityErrorCode.SESSION_NOT_FOUND);
        }
        sessionRevoker.revokeBySessionId(session.sessionIdHash());

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_SESSION_REVOKED")
                .meta("session_id", sessionId.toString())
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    /** A7 — dang xuat. Phien hien tai chi tho khi logout, do Spring Session lo. */
    @Transactional
    public void logout(UUID userId, AuthRequestContext context) {
        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_LOGOUT")
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    /** B6 {@code POST /account/password} — doi mat khau, thu hhoi phien khac. */
    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword,
                               AuthRequestContext context) {
        RateLimiter.RetryAfter retryAfter =
                rateLimiter.rejectIfLimited(RateLimitRule.ACCOUNT_PASSWORD, userId.toString());
        if (retryAfter != null) {
            throw new RateLimitedException(IdentityErrorCode.RATE_LIMITED,
                    retryAfter.headerSeconds(), "USER");
        }

        UserIdentity identity = identityRepository.findLocal(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.UNAUTHENTICATED));
        if (!identity.hasPassword()) {
            throw new BusinessRuleException(IdentityErrorCode.REAUTH_METHOD_UNAVAILABLE);
        }
        if (!passwordHasher.matches(currentPassword, identity.passwordHash())) {
            auditLogService.record(AuditEvent.builder()
                    .subjectUser(userId)
                    .action("AUTH_PASSWORD_CHANGE")
                    .denied()
                    .meta("reason", "wrong_current_password")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .build());
            // 401 REAUTH_FAILED chu khong phai INVALID_CREDENTIALS: phien van con, chi
            // buoc xac nhan sai (p8 §8.2.4).
            throw new BusinessRuleException(IdentityErrorCode.REAUTH_FAILED);
        }
        if (passwordHasher.matches(newPassword, identity.passwordHash())) {
            throw new BusinessRuleException(IdentityErrorCode.PASSWORD_REUSED);
        }
        PasswordPolicy.Violation violation = passwordPolicy.check(newPassword);
        if (violation != null) {
            throw switch (violation) {
                case TOO_SHORT, TOO_LONG ->
                        new BusinessRuleException(IdentityErrorCode.PASSWORD_TOO_WEAK);
                case REPEATED_CHARS, SEQUENTIAL_CHARS, BLOCKED ->
                        new BusinessRuleException(IdentityErrorCode.PASSWORD_BREACHED);
            };
        }

        Instant now = clock.instant();
        accountRepository.updatePasswordHash(userId, passwordHasher.hash(newPassword));
        accountRepository.updatePasswordTimestamps(userId, now, null);
        int revoked = sessionRevoker.revokeAllByUserId(userId);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_PASSWORD_CHANGED")
                .meta("sessions_revoked", revoked)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    /**
     * Step-up p11 §11.12.4. Client goi {@code POST /auth/reauth} roi <b>goi lai nguyen
     * request cu</b> — server so sanh thuoc tinh phien, khong co gi duoc gui them.
     */
    public void reauthenticate(UUID userId, String method, String secret, AuthRequestContext context) {
        // Bug that da sua: o day truoc kia co
        //     if (!sessionGateway.isReauthenticated()) throw REAUTH_REQUIRED;
        // — dieu kien NGUOC. Chinh endpoint nay la thu LAM CHO phien tro thanh reauthenticated,
        // nen tren mot phien binh thuong (chua step-up) no luon nem 403 va khong bao gio chay
        // toi buoc xac minh credential ⇒ toan bo co che step-up (p11 §11.12.4) la code chet:
        // moi thao tac nhay cam doi S1/SW khong the thuc hien duoc. Xac nhan that bang
        // POST /auth/reauth tren phien vua dang nhap: 403 REAUTH_REQUIRED.
        // Sai credential van bi chan boi REAUTH_FAILED ben duoi.
        if (method == null) {
            throw new PermissionDeniedException(IdentityErrorCode.REAUTH_REQUIRED);
        }
        boolean ok = switch (method.toUpperCase(java.util.Locale.ROOT)) {
            case "PASSWORD" -> verifyPassword(userId, secret);
            case "EMAIL_OTP" -> verifyEmailOtp(userId, secret, context);
            case "TOTP" -> false;
            default -> false;
        };
        if (!ok) {
            throw new BusinessRuleException(IdentityErrorCode.REAUTH_FAILED);
        }
        sessionGateway.markReauthenticated(300);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_REAUTH")
                .meta("method", method)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .build());
    }

    private boolean verifyPassword(UUID userId, String secret) {
        UserIdentity identity = identityRepository.findLocal(userId).orElse(null);
        if (identity == null || !identity.hasPassword()) {
            return false;
        }
        return passwordHasher.matches(secret, identity.passwordHash());
    }

    private boolean verifyEmailOtp(UUID userId, String rawTicket, AuthRequestContext context) {
        if (sessionGateway.currentEmail().isEmpty() || rawTicket == null) {
            return false;
        }
        try {
            // TIE ticket ngay trong buoc step-up: dang xac nhan thi ticket phai mat.
            return otpService.consumeTicket(rawTicket, OtpPurpose.LOGIN_STEPUP)
                    .userId()
                    .equals(userId);
        } catch (BusinessRuleException ex) {
            auditLogService.record(AuditEvent.builder()
                    .subjectUser(userId)
                    .action("AUTH_REAUTH")
                    .denied()
                    .meta("reason", "bad_ticket")
                    .requestId(context.requestId())
                    .build());
            return false;
        }
    }

    /** Mot dong trong {@code GET /auth/sessions}. KHONG lo {@code sessionIdHash}. */
    public record SessionView(
            UUID id,
            String deviceLabel,
            String userAgent,
            String ipAddress,
            Instant createdAt,
            Instant lastSeenAt,
            Instant expiresAt,
            boolean rememberMe,
            boolean current) {

        static SessionView of(DeviceSession session, String currentSessionIdHash) {
            return new SessionView(
                    session.id(),
                    session.deviceLabel(),
                    session.userAgent(),
                    session.ipAddress(),
                    session.createdAt(),
                    session.lastSeenAt(),
                    session.expiresAt(),
                    session.rememberMe(),
                    session.isCurrent(currentSessionIdHash));
        }
    }

    /** Han phien cho moi phien: 30 ngay neu "nho toi", 12 gio neu khong. */
    public java.time.Duration maxInactiveFor(boolean rememberMe) {
        return rememberMe ? policy.sessionMaxInactive() : policy.sessionDefaultMaxInactive();
    }
}
