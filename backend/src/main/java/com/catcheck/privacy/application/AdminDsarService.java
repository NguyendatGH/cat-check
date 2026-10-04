package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Nghiệp vụ L51/L54 — xử lý DSAR từ hàng chờ quản trị. */
@Service
public class AdminDsarService {

    private static final Set<String> SUPPORT_ACTIONS = Set.of("ACK", "ASSIGN");

    private final DsarService dsarService;
    private final DsarRequestPort dsarPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminDsarService(
            DsarService dsarService,
            DsarRequestPort dsarPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.dsarService = dsarService;
        this.dsarPort = dsarPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public DsarRequest create(
            UUID actorId,
            String actorRole,
            UUID userId,
            DsarRequestType requestType,
            DsarChannel channel,
            String reason,
            RequestEvidence evidence) {
        DsarRequest created = dsarService.createAdminRequest(userId, requestType, channel);
        audit(actorId, actorRole, created, "DSAR_CREATED_ADMIN", reason, evidence,
                null, created.status().name());
        return created;
    }

    @Transactional
    public DsarRequest transition(
            UUID actorId,
            String actorRole,
            UUID requestId,
            String actionValue,
            String reason,
            Instant extendedTo,
            RequestEvidence evidence) {
        String action = actionValue == null ? "" : actionValue.strip().toUpperCase(Locale.ROOT);
        DsarRequest request = dsarPort.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if ("ADMIN_SUPPORT".equals(actorRole) && !SUPPORT_ACTIONS.contains(action)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, "ACK, ASSIGN");
        }
        if (action.isBlank()) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "action");
        }
        if (request.status() == DsarStatus.COMPLETED || request.status() == DsarStatus.REJECTED) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "terminalStatus");
        }

        Instant now = clock.instant();
        DsarRequest updated = switch (action) {
            case "ACK" -> request.withAckSentAt(request.ackSentAt() == null ? now : request.ackSentAt());
            case "ASSIGN" -> {
                DsarStatus next = request.requiresIdentityVerification() && request.identityVerifiedAt() == null
                        ? DsarStatus.IDENTITY_PENDING : DsarStatus.IN_PROGRESS;
                yield request.withHandledBy(actorId).withStatus(next);
            }
            case "EXTEND" -> extend(request, extendedTo, reason);
            case "COMPLETE" -> complete(request, now);
            case "REJECT" -> request.withRejectionReason(reason).withStatus(DsarStatus.REJECTED);
            default -> throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "action");
        };
        dsarPort.update(updated);
        audit(actorId, actorRole, updated, "DSAR_STATUS_CHANGE", reason, evidence,
                request.status().name(), updated.status().name(), action);
        return updated;
    }

    private DsarRequest extend(DsarRequest request, Instant extendedTo, String reason) {
        if (request.extendedTo() != null || extendedTo == null) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "extendedTo");
        }
        Duration original = Duration.between(request.receivedAt(), request.fulfilDueAt());
        Instant maxDue = request.fulfilDueAt().plus(original);
        if (!extendedTo.isAfter(request.fulfilDueAt()) || extendedTo.isAfter(maxDue)) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "extendedTo");
        }
        return request.withExtension(extendedTo, reason).withStatus(DsarStatus.EXTENDED);
    }

    private DsarRequest complete(DsarRequest request, Instant now) {
        if (request.requestType() == DsarRequestType.ACCESS_EXPORT && request.resultRef() == null) {
            throw new BusinessRuleException(PrivacyErrorCode.DSAR_EXPORT_NOT_READY, request.status().name());
        }
        if (request.requestType() == DsarRequestType.ERASE) {
            // L53 must run the two-person erasure executor before ERASE can become complete.
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "erasureApprovalRequired");
        }
        return request.withStatus(DsarStatus.COMPLETED).withCompletedAt(now);
    }

    private void audit(
            UUID actorId,
            String actorRole,
            DsarRequest request,
            String action,
            String reason,
            RequestEvidence evidence,
            String from,
            String to,
            String... extra) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor("DPO".equals(actorRole) ? AuditActor.dpo(actorId, actorRole) : AuditActor.admin(actorId, actorRole))
                .subject(AuditSubjectType.USER, request.userId())
                .action(action)
                .outcome(AuditOutcome.SUCCESS)
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("requestId", request.id())
                .meta("publicRef", request.publicRef());
        if (reason != null && !reason.isBlank()) {
            builder.meta("reason", reason.strip());
        }
        if (from != null) {
            builder.meta("from", from).meta("to", to);
        }
        if (extra.length > 0) {
            builder.meta("transitionAction", extra[0]);
        }
        auditLogService.record(builder.build());
    }
}
