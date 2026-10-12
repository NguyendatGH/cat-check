package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.identity.api.CryptoPurpose;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.api.PiiCipher;
import com.catcheck.identity.domain.AdminUserSummary;
import com.catcheck.identity.domain.DeviceSession;
import com.catcheck.shared.security.PiiMask;
import com.catcheck.identity.domain.SessionRevokeReason;
import com.catcheck.identity.domain.UserAccount;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.UserStatus;
import com.catcheck.identity.domain.port.AdminUserQueryPort;
import com.catcheck.identity.domain.port.DeviceSessionRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AdminUserService {

    public static final Duration UNMASK_WINDOW = Duration.ofMinutes(15);

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private static final String PHONE_TABLE = "app_user";
    private static final String PHONE_COLUMN = "phone";

    private final AdminUserQueryPort userQueryPort;
    private final UserAccountRepository accountRepository;
    private final UserRoleRepository roleRepository;
    private final DeviceSessionRepository sessionRepository;
    private final MfaTotpRepository totpRepository;
    private final PiiCipher piiCipher;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminUserService(AdminUserQueryPort userQueryPort,
                            UserAccountRepository accountRepository,
                            UserRoleRepository roleRepository,
                            DeviceSessionRepository sessionRepository,
                            MfaTotpRepository totpRepository,
                            PiiCipher piiCipher,
                            AuditLogService auditLogService,
                            Clock clock) {
        this.userQueryPort = userQueryPort;
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
        this.totpRepository = totpRepository;
        this.piiCipher = piiCipher;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }


    @Transactional
    public Page<AdminUserSummary> search(UserStatus status, String email, String highestPackage,
                                         int page, int size, AdminActionContext context) {
        List<AdminUserSummary> items =
                userQueryPort.search(status, email, highestPackage, page * size, size);
        long total = userQueryPort.count(status, email, highestPackage);

        auditLogService.record(base(context, "ADMIN_USER_SEARCH", null)
                .meta("status", status == null ? null : status.name())
                .meta("byEmail", email != null && !email.isBlank())
                .meta("highestPackage", highestPackage)
                .meta("resultCount", items.size())
                .build());
        return new Page<>(items, total);
    }


    @Transactional
    public AdminUserDetail detail(UUID userId, AdminActionContext context) {
        AdminUserSummary summary = requireSummary(userId);
        UserAccount account = requireAccount(userId);
        Instant now = clock.instant();
        List<UserRole> roles = roleRepository.findByUserId(userId);
        int activeSessions = sessionRepository.findActiveByUserId(userId, now).size();
        boolean totpEnabled = totpRepository.findByUserId(userId)
                .map(totp -> totp.isActive())
                .orElse(false);

        auditLogService.record(base(context, "ADMIN_USER_VIEW", userId).build());

        return new AdminUserDetail(summary, account, roles, activeSessions, totpEnabled,
                PiiMask.phone(decryptPhone(account)));
    }


    @Transactional
    public UnmaskedPii unmask(UUID userId, AdminActionContext context) {
        UserAccount account = requireAccount(userId);
        String phone = decryptPhone(account);

        auditLogService.record(base(context, "ADMIN_PII_UNMASKED", userId)
                .meta("fields", phone == null ? List.of("email") : List.of("email", "phone"))
                .meta("windowMinutes", UNMASK_WINDOW.toMinutes())
                .build());

        log.info("Admin bỏ mask PII: actor={} subject={}", context.adminId(), userId);
        return new UnmaskedPii(
                userId, account.email().value(), phone, clock.instant().plus(UNMASK_WINDOW));
    }


    @Transactional
    public LockResult lock(UUID userId, AdminActionContext context) {
        requireNotSelf(userId, context);
        UserAccount account = requireAccount(userId);
        if (account.status() == UserStatus.ANONYMIZED
                || account.status() == UserStatus.DELETION_REQUESTED
                || account.status() == UserStatus.RESTRICTED) {
            throw new ConflictException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        Instant now = clock.instant();
        accountRepository.updateLoginState(
                userId, UserStatus.LOCKED, account.failedLoginCount(), null, account.lastLoginAt());
        int revoked = sessionRepository.revokeAllByUserId(userId, now, SessionRevokeReason.ADMIN_LOCK);

        auditLogService.record(base(context, "ADMIN_LOCK_ACCOUNT", userId)
                .before(java.util.Map.of("status", account.status().name()))
                .after(java.util.Map.of("status", UserStatus.LOCKED.name()))
                .meta("revokedSessions", revoked)
                .build());

        log.info("Admin khoá tài khoản: actor={} subject={} revokedSessions={}",
                context.adminId(), userId, revoked);
        return new LockResult(userId, UserStatus.LOCKED, revoked);
    }

    @Transactional
    public LockResult unlock(UUID userId, AdminActionContext context) {
        UserAccount account = requireAccount(userId);
        if (account.status() != UserStatus.LOCKED) {
            throw new ConflictException(IdentityErrorCode.ACCOUNT_RESTRICTED);
        }
        accountRepository.updateLoginState(
                userId, UserStatus.ACTIVE, 0, null, account.lastLoginAt());

        auditLogService.record(base(context, "ADMIN_UNLOCK_ACCOUNT", userId)
                .before(java.util.Map.of("status", UserStatus.LOCKED.name()))
                .after(java.util.Map.of("status", UserStatus.ACTIVE.name()))
                .build());

        log.info("Admin mở khoá tài khoản: actor={} subject={}", context.adminId(), userId);
        return new LockResult(userId, UserStatus.ACTIVE, 0);
    }


    @Transactional
    public List<DeviceSession> sessions(UUID userId, AdminActionContext context) {
        requireSummary(userId);
        List<DeviceSession> sessions = sessionRepository.findActiveByUserId(userId, clock.instant());
        auditLogService.record(base(context, "ADMIN_USER_SESSIONS_VIEW", userId)
                .meta("sessionCount", sessions.size())
                .build());
        return sessions;
    }

    @Transactional
    public int revokeAllSessions(UUID userId, AdminActionContext context) {
        requireSummary(userId);
        int revoked = sessionRepository.revokeAllByUserId(
                userId, clock.instant(), SessionRevokeReason.USER_REVOKE_ALL);
        auditLogService.record(base(context, "ADMIN_USER_SESSIONS_REVOKE", userId)
                .meta("revokedSessions", revoked)
                .build());
        log.info("Admin thu hồi phiên: actor={} subject={} revokedSessions={}",
                context.adminId(), userId, revoked);
        return revoked;
    }


    private AuditEvent.Builder base(AdminActionContext context, String action, UUID subjectUserId) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(context.auditActor())
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent());
        if (subjectUserId != null) {
            builder.subjectUser(subjectUserId);
        }
        if (context.reason() != null) {
            builder.meta("reason", context.reason());
        }
        return builder;
    }

    private void requireNotSelf(UUID userId, AdminActionContext context) {
        if (userId.equals(context.adminId())) {
            throw new ConflictException(IdentityErrorCode.ADMIN_CANNOT_MODIFY_SELF);
        }
    }

    private AdminUserSummary requireSummary(UUID userId) {
        return userQueryPort.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
    }

    private UserAccount requireAccount(UUID userId) {
        return accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
    }

    private String decryptPhone(UserAccount account) {
        if (account.phone() == null) {
            return null;
        }
        byte[] plaintext = piiCipher.decrypt(CryptoPurpose.PII_PHONE,
                PiiCipher.aad(PHONE_TABLE, PHONE_COLUMN, account.id()),
                account.phone());
        return plaintext == null ? null : new String(plaintext, StandardCharsets.UTF_8);
    }

    public record AdminUserDetail(
            AdminUserSummary summary,
            UserAccount account,
            List<UserRole> roles,
            int activeSessionCount,
            boolean totpEnabled,
            String phoneMasked) {
    }

    public record UnmaskedPii(UUID userId, String email, String phone, Instant unmaskedUntil) {
    }

    public record LockResult(UUID userId, UserStatus status, int revokedSessions) {
    }

    public record Page<T>(List<T> items, long totalElements) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record AdminActionContext(
            UUID adminId,
            String adminRole,
            String reason,
            String requestId,
            String ipAddress,
            String userAgent) {

        public AdminActionContext {
            if (adminId == null) {
                throw new IllegalArgumentException("adminActionContext.adminId bắt buộc");
            }
        }

        public AuditActor auditActor() {
            return "DPO".equals(adminRole)
                    ? AuditActor.dpo(adminId, adminRole)
                    : AuditActor.admin(adminId, adminRole);
        }
    }
}
