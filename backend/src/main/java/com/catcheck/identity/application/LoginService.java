package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.AuthPolicy;
import com.catcheck.identity.domain.EmailAddress;
import com.catcheck.identity.domain.RateLimitRule;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserIdentity;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.PasswordHasher;
import com.catcheck.identity.domain.port.RateLimiter;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserIdentityRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class LoginService {

    private final UserAccountRepository accountRepository;
    private final UserIdentityRepository identityRepository;
    private final UserRoleRepository roleRepository;
    private final PasswordHasher passwordHasher;
    private final AuthPolicy policy;
    private final RateLimiter rateLimiter;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public LoginService(UserAccountRepository accountRepository,
                        UserIdentityRepository identityRepository,
                        UserRoleRepository roleRepository,
                        PasswordHasher passwordHasher,
                        AuthPolicy policy,
                        RateLimiter rateLimiter,
                        AuthenticatedSessionRevoker sessionRevoker,
                        AuditLogService auditLogService,
                        Clock clock) {
        this.accountRepository = accountRepository;
        this.identityRepository = identityRepository;
        this.roleRepository = roleRepository;
        this.passwordHasher = passwordHasher;
        this.policy = policy;
        this.rateLimiter = rateLimiter;
        this.sessionRevoker = sessionRevoker;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    public record LoginResult(UUID userId, EmailAddress email, String fullName, Set<String> roles) {
    }

    // Bo dem sai / khoa ghi truoc khi nem loi phai duoc commit, khong rollback theo exception.
    @Transactional(noRollbackFor = BusinessRuleException.class)
    public LoginResult login(String rawEmail, String rawPassword, boolean rememberMe,
                             AuthRequestContext context) {
        EmailAddress email = EmailAddress.parseOrNull(rawEmail);
        String lookupEmail = email == null ? normalizeBlind(rawEmail) : email.value();

        requireRateLimit(lookupEmail, context);

        Optional<UserAccount> found = email == null
                ? Optional.empty()
                : accountRepository.findByEmail(email);
        UserIdentity identity = found
                .flatMap(account -> identityRepository.findLocal(account.id()))
                .orElse(null);

        String storedHash = identity != null && identity.hasPassword()
                ? identity.passwordHash()
                : passwordHasher.dummyHash();
        boolean passwordOk = passwordHasher.matches(rawPassword, storedHash);

        if (found.isEmpty() || identity == null || !passwordOk) {
            recordFailure(found.map(UserAccount::id).orElse(null), email, context);
            throw new BusinessRuleException(IdentityErrorCode.INVALID_CREDENTIALS);
        }

        UserAccount account = found.get();
        Instant now = clock.instant();

        if (account.lockedUntil() != null && account.lockedUntil().isAfter(now)) {
            auditDenied(account.id(), "account_locked", context);
            throw new BusinessRuleException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (account.status() == UserStatus.LOCKED) {
            auditDenied(account.id(), "account_locked", context);
            throw new BusinessRuleException(IdentityErrorCode.INVALID_CREDENTIALS);
        }
        if (account.status() != UserStatus.ACTIVE || account.emailVerifiedAt() == null) {
            auditDenied(account.id(), "not_active", context);
            if (account.emailVerifiedAt() == null) {
                throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_NOT_VERIFIED);
            }
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        if (account.isProcessingRestricted()) {
            auditDenied(account.id(), "processing_restricted", context);
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }

        int activeSessions = countActiveSessions(account.id(), now);
        if (activeSessions >= policy.maxConcurrentSessions()) {
            throw new BusinessRuleException(IdentityErrorCode.SESSION_LIMIT_REACHED,
                    policy.maxConcurrentSessions());
        }

        accountRepository.updateLoginState(account.id(), UserStatus.ACTIVE, 0, null, now);

        List<UserRole> roles = roleRepository.findByUserId(account.id());
        identityRepository.touchLastUsed(identity.id(), now);

        auditLogService.record(AuditEvent.builder()
                .subjectUser(account.id())
                .action("AUTH_LOGIN")
                .meta("remember_me", rememberMe)
                .meta("active_sessions", activeSessions + 1)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());

        return new LoginResult(
                account.id(),
                account.email(),
                account.fullName(),
                roles.stream().map(Enum::name).collect(java.util.stream.Collectors.toUnmodifiableSet()));
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

    /** Kiem tra lai truoc khi mo phien tu trang thai cho TOTP: tai khoan co the da bi khoa trong luc cho. */
    @Transactional(readOnly = true)
    public void requireSessionAllowed(UUID userId) {
        UserAccount account = accountRepository.findById(userId)
                .orElseThrow(() -> new BusinessRuleException(IdentityErrorCode.UNAUTHENTICATED));
        Instant now = clock.instant();
        if (account.status() != UserStatus.ACTIVE || account.isProcessingRestricted()
                || (account.lockedUntil() != null && account.lockedUntil().isAfter(now))) {
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
    }

    private int countActiveSessions(UUID userId, Instant now) {
        return sessionRevoker.activeSessionIdsByUserId(userId).size();
    }

    private void recordFailure(UUID userId, EmailAddress email, AuthRequestContext context) {
        if (userId == null) {
            auditLogService.record(AuditEvent.builder()
                    .action("AUTH_LOGIN")
                    .denied()
                    .meta("reason", "unknown_account")
                    .requestId(context.requestId())
                    .ipAddress(context.ipAddress())
                    .build());
            return;
        }
        UserAccount account = accountRepository.findById(userId).orElse(null);
        if (account == null) {
            return;
        }
        Instant now = clock.instant();
        // Dang bi khoa thi khong dem them, tranh keo dai khoa / day len LOCKED.
        if ((account.lockedUntil() != null && account.lockedUntil().isAfter(now))
                || account.status() == UserStatus.LOCKED) {
            auditDenied(userId, "account_locked", context);
            return;
        }
        int failed = account.failedLoginCount() + 1;
        UserStatus nextStatus = account.status();

        if (failed >= policy.lockoutThresholdLong()) {
            nextStatus = UserStatus.LOCKED;
            accountRepository.updateLoginState(userId, nextStatus, failed,
                    now.plus(policy.lockoutDurationLong()), account.lastLoginAt());
            auditDenied(userId, "locked_long", context);
        } else if (failed >= policy.lockoutThresholdShort()) {
            accountRepository.updateLoginState(userId, nextStatus, failed,
                    now.plus(policy.lockoutDurationShort()), account.lastLoginAt());
            auditDenied(userId, "locked_short", context);
        } else {
            accountRepository.updateLoginState(userId, nextStatus, failed, null, account.lastLoginAt());
            auditDenied(userId, "wrong_password", context);
        }
    }

    private void auditDenied(UUID userId, String reason, AuthRequestContext context) {
        auditLogService.record(AuditEvent.builder()
                .subjectUser(userId)
                .action("AUTH_LOGIN")
                .denied()
                .meta("reason", reason)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .build());
    }

    private void requireRateLimit(String lookupEmail, AuthRequestContext context) {
        RateLimiter.RetryAfter byEmail = rateLimiter.rejectIfLimited(RateLimitRule.LOGIN_BY_EMAIL, lookupEmail);
        if (byEmail != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byEmail.headerSeconds(), "EMAIL");
        }
        RateLimiter.RetryAfter byIp = rateLimiter.rejectIfLimited(RateLimitRule.LOGIN_BY_IP, context.ipAddress());
        if (byIp != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byIp.headerSeconds(), "IP");
        }
    }

    private static String normalizeBlind(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
