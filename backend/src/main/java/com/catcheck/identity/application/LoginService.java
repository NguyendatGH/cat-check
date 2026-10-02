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

/**
 * Dang nhap (p8 A6, p11 §11.1.5, §11.7.2).
 *
 * <p><b>Quan trong nhat cua ca module: khong lo tai khoan co ton tai.</b> Ba ve khac nhau
 * — email sai, mat khau sai, tai khoan bi khoa — deu tra <b>cung mot</b> ma
 * {@link IdentityErrorCode#INVALID_CREDENTIALS} va phai ton tai <b>cung thoi gian phan
 * hoi</b>. Do do khi khong tim thay tai khoan, van phai chay BCrypt tren hash gia
 * ({@link PasswordHasher#dummyHash()}).</p>
 *
 * <p>Rate limit chay truoc ca tra DB: 5/phut theo email + 10/phut theo IP
 * (p11 §11.7.2).</p>
 */
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

    /** Ket qua dang nhap: controller dung {@code userId} + {@code roles} de mo phien. */
    public record LoginResult(UUID userId, EmailAddress email, String fullName, Set<String> roles) {
    }

    @Transactional
    public LoginResult login(String rawEmail, String rawPassword, boolean rememberMe,
                             AuthRequestContext context) {
        EmailAddress email = EmailAddress.parseOrNull(rawEmail);
        String lookupEmail = email == null ? normalizeBlind(rawEmail) : email.value();

        // Rate limit dung email CHUA chuan hoa khi email khong hop le — nguoi goi khac
        // nghia dang ky 1 lan ma van khong lo doi do loc. Dung 1 khoa cho ca ca hai nhanh.
        requireRateLimit(lookupEmail);

        Optional<UserAccount> found = email == null
                ? Optional.empty()
                : accountRepository.findByEmail(email);
        UserIdentity identity = found
                .flatMap(account -> identityRepository.findLocal(account.id()))
                .orElse(null);

        // Nhanh "khong co tai khoan" van phai ton thoi gian BCrypt.
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

        // Khoa tam thoi theo so lan sai (p11 §11.7.2). Kiem tra TRUOC khi xet ACTIVE:
        // tai khoan da LOCKED van phai tra INVALID_CREDENTIALS chu khong phai mot ma khac.
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
            // Client da gui mat khau dung -> no khong phai nguoi la, nen noi ro tai khoan
            // chua xac minh moi an toan (p8 §8.2.4 ACCOUNT_NOT_VERIFIED).
            if (account.emailVerifiedAt() == null) {
                throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_NOT_VERIFIED);
            }
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        if (account.isProcessingRestricted()) {
            auditDenied(account.id(), "processing_restricted", context);
            throw new BusinessRuleException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }

        // Ket qua dung: reset bo dem sai va ghi lan dang nhap gan nhat.
        accountRepository.updateLoginState(account.id(), UserStatus.ACTIVE, 0, null, now);

        int activeSessions = countActiveSessions(account.id(), now);
        if (activeSessions >= policy.maxConcurrentSessions()) {
            // p8 §8.2.4 SESSION_LIMIT_REACHED: thong bao thay vi thu hoi ngam — nguoi
            // dung phai duoc chon phien nao de giu.
            throw new BusinessRuleException(IdentityErrorCode.SESSION_LIMIT_REACHED,
                    policy.maxConcurrentSessions());
        }

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

    /**
     * Dang xuat (p8 A7). Khong the huy phien hien tai qua API (p8 A7 + A13) — phien hien
     * tai chi tho khi logout, do Spring Session lo.
     */
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

    private void requireRateLimit(String lookupEmail) {
        RateLimiter.RetryAfter byEmail = rateLimiter.rejectIfLimited(RateLimitRule.LOGIN_BY_EMAIL, lookupEmail);
        if (byEmail != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byEmail.headerSeconds(), "EMAIL");
        }
        RateLimiter.RetryAfter byIp = rateLimiter.rejectIfLimited(RateLimitRule.LOGIN_BY_IP, null);
        if (byIp != null) {
            throw new com.catcheck.shared.error.RateLimitedException(
                    IdentityErrorCode.RATE_LIMITED, byIp.headerSeconds(), "IP");
        }
    }

    /**
     * Khi email khong dung dinh dang, van can mot khoa rate limit ổn dinh de khong bi
     * bo qua bang cach gui email sai hinh thuc. Chuoi rong tra rong — khoa rong thi
     * {@link RateLimiter} bo qua quy tac, va nhanh IP van chan.
     */
    private static String normalizeBlind(String rawEmail) {
        if (rawEmail == null) {
            return "";
        }
        return rawEmail.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
