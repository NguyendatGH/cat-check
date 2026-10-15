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

    public List<SessionView> listSessions(UUID userId) {
        Instant now = clock.instant();
        String currentHash = sessionGateway.currentSessionIdHash().orElse(null);
        java.util.Set<String> live = sessionRevoker.activeSessionIdsByUserId(userId);
        return sessionRepository.findActiveByUserId(userId, now).stream()
                .filter(session -> live.contains(session.sessionIdHash()))
                .map(session -> SessionView.of(session, currentHash))
                .toList();
    }

    @Transactional
    public int revokeOtherSessions(UUID userId, AuthRequestContext context) {
        int revoked = revokeAllExceptCurrent(userId);
        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_SESSION_REVOKED")
                .meta("scope", "OTHERS")
                .meta("sessions_revoked", revoked)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
        return revoked;
    }

    private int revokeAllExceptCurrent(UUID userId) {
        String currentSessionIdHash = sessionGateway.currentSessionIdHash().orElse(null);
        int revoked = 0;
        for (String sessionIdHash : sessionRevoker.activeSessionIdsByUserId(userId)) {
            if (!sessionIdHash.equals(currentSessionIdHash)) {
                sessionRevoker.revokeBySessionId(sessionIdHash);
                revoked++;
            }
        }
        return revoked;
    }

    @Transactional
    public void revokeSession(UUID userId, UUID sessionId, AuthRequestContext context) {
        DeviceSession session = sessionRepository.findById(sessionId)
                .filter(candidate -> candidate.userId().equals(userId))
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
        int revoked = revokeAllExceptCurrent(userId);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_PASSWORD_CHANGED")
                .meta("sessions_revoked", revoked)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    public void reauthenticate(UUID userId, String method, String secret, AuthRequestContext context) {
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
            // Challenge khong co chu (userId null) khong bao gio thoa step-up.
            return userId.equals(otpService.consumeTicket(rawTicket, OtpPurpose.LOGIN_STEPUP).userId());
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

    public java.time.Duration maxInactiveFor(boolean rememberMe) {
        return rememberMe ? policy.sessionMaxInactive() : policy.sessionDefaultMaxInactive();
    }
}
