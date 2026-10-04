package com.catcheck.identity.application;

import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.identity.api.IdentityErrorCode;
import com.catcheck.identity.domain.MfaResetRequest;
import com.catcheck.identity.domain.MfaResetRequestStatus;
import com.catcheck.identity.domain.UserRole;
import com.catcheck.identity.domain.port.MfaRecoveryCodeRepository;
import com.catcheck.identity.domain.port.MfaResetRequestRepository;
import com.catcheck.identity.domain.port.MfaTotpRepository;
import com.catcheck.identity.domain.port.AuthenticatedSessionRevoker;
import com.catcheck.identity.domain.port.UserAccountRepository;
import com.catcheck.identity.domain.port.UserRoleRepository;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** L13-L17 — role quản trị và reset TOTP theo quy tắc hai người. */
@Service
public class AdminStaffService {

    /** Khớp app_setting.mfa.reset_request_ttl_hours trong seed (24 giờ). */
    private static final Duration REQUEST_TTL = Duration.ofHours(24);
    private static final int MAX_REQUESTS_PER_DAY = 3;

    private final UserAccountRepository accountRepository;
    private final UserRoleRepository roleRepository;
    private final MfaResetRequestRepository resetRepository;
    private final MfaTotpRepository totpRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final AuthenticatedSessionRevoker sessionRevoker;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminStaffService(UserAccountRepository accountRepository,
                             UserRoleRepository roleRepository,
                             MfaResetRequestRepository resetRepository,
                             MfaTotpRepository totpRepository,
                             MfaRecoveryCodeRepository recoveryCodeRepository,
                             AuthenticatedSessionRevoker sessionRevoker,
                             AuditLogService auditLogService,
                             Clock clock) {
        this.accountRepository = accountRepository;
        this.roleRepository = roleRepository;
        this.resetRepository = resetRepository;
        this.totpRepository = totpRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.sessionRevoker = sessionRevoker;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public void grantRole(UUID userId, UserRole role, AdminUserService.AdminActionContext context) {
        requireRole(context, "ADMIN_SUPER");
        requireTarget(userId);
        requireAssignableRole(role);
        requireNotSelf(userId, context);
        roleRepository.grant(userId, role, context.adminId());
        audit(context, userId, "ADMIN_ROLE_GRANTED", role.name());
    }

    @Transactional
    public void revokeRole(UUID userId, UserRole role, AdminUserService.AdminActionContext context) {
        requireRole(context, "ADMIN_SUPER");
        requireTarget(userId);
        requireAssignableRole(role);
        requireNotSelf(userId, context);
        roleRepository.revoke(userId, role);
        audit(context, userId, "ADMIN_ROLE_REVOKED", role.name());
    }

    @Transactional
    public MfaResetRequest requestTotpReset(UUID targetUserId, AdminUserService.AdminActionContext context) {
        requireRole(context, "ADMIN_SUPER");
        requireTarget(targetUserId);
        requireNotSelf(targetUserId, context);
        Instant now = clock.instant();
        if (resetRepository.countRequestedBySince(context.adminId(), now.minus(Duration.ofHours(24)))
                >= MAX_REQUESTS_PER_DAY) {
            throw new ConflictException(IdentityErrorCode.MFA_RESET_RATE_LIMITED);
        }
        if (resetRepository.findPendingByTargetUserId(targetUserId).isPresent()) {
            throw new ConflictException(IdentityErrorCode.MFA_RESET_PENDING);
        }
        MfaResetRequest request = new MfaResetRequest(
                UUID.randomUUID(), targetUserId, context.adminId(), now, context.reason(), null, null,
                MfaResetRequestStatus.PENDING, null, now.plus(REQUEST_TTL), null, now, now);
        resetRepository.insert(request);
        audit(context, targetUserId, "ADMIN_MFA_RESET_REQUESTED", request.id().toString());
        return request;
    }

    @Transactional(readOnly = true)
    public List<MfaResetRequest> pendingResetRequests() {
        return resetRepository.findPendingQueue();
    }

    @Transactional
    public MfaResetRequest approveTotpReset(UUID requestId, AdminUserService.AdminActionContext context) {
        requireRole(context, "DPO");
        MfaResetRequest request = resetRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
        if (request.status() != MfaResetRequestStatus.PENDING || request.expiresAt().isBefore(clock.instant())) {
            throw new ConflictException(IdentityErrorCode.MFA_RESET_NOT_PENDING);
        }
        if (request.requestedBy().equals(context.adminId())) {
            throw new ConflictException(IdentityErrorCode.MFA_RESET_TWO_PERSON_REQUIRED);
        }
        requireNotSelf(request.targetUserId(), context);
        Instant now = clock.instant();
        // Reset xoá secret và recovery codes; người dùng phải enroll lại MFA khi đăng nhập lần sau.
        totpRepository.delete(request.targetUserId());
        recoveryCodeRepository.deleteByUserId(request.targetUserId());
        int revokedSessions = sessionRevoker.revokeAllByUserId(request.targetUserId());
        resetRepository.markApproved(request.id(), context.adminId(), now);
        audit(context, request.targetUserId(), "AUTH.TOTP_RESET", request.id().toString(), revokedSessions);
        return resetRepository.findById(request.id()).orElse(request);
    }

    private void requireTarget(UUID userId) {
        accountRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(IdentityErrorCode.NOT_FOUND));
    }

    private static void requireAssignableRole(UserRole role) {
        if (role == null || role == UserRole.ADMIN_SUPER || role == UserRole.DPO || role == UserRole.USER) {
            throw new ConflictException(IdentityErrorCode.ROLE_NOT_ASSIGNABLE);
        }
    }

    private static void requireRole(AdminUserService.AdminActionContext context, String requiredRole) {
        if (!requiredRole.equals(context.adminRole())) {
            throw new com.catcheck.shared.error.PermissionDeniedException(
                    com.catcheck.shared.security.AdminApiErrorCode.ACCESS_DENIED, requiredRole);
        }
    }

    private static void requireNotSelf(UUID userId, AdminUserService.AdminActionContext context) {
        if (userId.equals(context.adminId())) {
            throw new ConflictException(IdentityErrorCode.ADMIN_CANNOT_MODIFY_SELF);
        }
    }

    private void audit(AdminUserService.AdminActionContext context, UUID subject, String action, String value) {
        audit(context, subject, action, value, null);
    }

    private void audit(AdminUserService.AdminActionContext context, UUID subject, String action, String value,
                       Integer revokedSessions) {
        AuditEvent.Builder event = AuditEvent.builder()
                .actor(context.auditActor())
                .subjectUser(subject)
                .action(action)
                .requestId(context.requestId())
                .ipAddress(context.ipAddress())
                .userAgent(context.userAgent())
                .meta("value", value)
                .meta("reason", context.reason());
        if (revokedSessions != null) {
            event.meta("revokedSessions", revokedSessions);
        }
        auditLogService.record(event.build());
    }
}
