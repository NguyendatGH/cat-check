package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.DsarChannel;
import com.catcheck.privacy.domain.DsarRequest;
import com.catcheck.privacy.domain.DsarRequestType;
import com.catcheck.privacy.domain.DsarStatus;
import com.catcheck.privacy.domain.port.DsarRequestPort;
import com.catcheck.privacy.spi.UserAccountPort;
import com.catcheck.privacy.spi.UserAccountSnapshot;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.PermissionDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class AdminErasureApprovalService {

    public static final String ROLE_DPO = "DPO";

    private final DsarRequestPort dsarPort;
    private final ErasureCoordinator erasureCoordinator;
    private final UserAccountPort userAccountPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AdminErasureApprovalService(
            DsarRequestPort dsarPort,
            ErasureCoordinator erasureCoordinator,
            UserAccountPort userAccountPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.dsarPort = dsarPort;
        this.erasureCoordinator = erasureCoordinator;
        this.userAccountPort = userAccountPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public Result approve(UUID approverId, UUID requestId, String reason, RequestEvidence evidence) {
        UserAccountSnapshot approver = userAccountPort.snapshot(approverId);
        if (!approver.hasRole(ROLE_DPO)) {
            throw new PermissionDeniedException(PrivacyErrorCode.FORBIDDEN, ROLE_DPO);
        }
        DsarRequest request = dsarPort.findById(requestId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.DSAR_NOT_FOUND));
        if (request.requestType() != DsarRequestType.ERASE) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "requestType");
        }
        if (request.status() == DsarStatus.COMPLETED || request.status() == DsarStatus.REJECTED) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "terminalStatus");
        }
        if (request.identityVerifiedAt() == null) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_IDENTITY_VERIFICATION_REQUIRED, request.publicRef());
        }
        requireSecondPerson(request, approverId);
        if (request.userId() == null) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "userId");
        }

        UUID subjectId = request.userId();
        erasureCoordinator.erase(subjectId);
        DsarRequest completed = request
                .withStatus(DsarStatus.COMPLETED)
                .withCompletedAt(clock.instant());
        dsarPort.update(completed);

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.dpo(approverId, ROLE_DPO))
                .subjectUser(subjectId)
                .action("DSAR_ERASURE_APPROVED")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("requestId", request.id())
                .meta("publicRef", request.publicRef())
                .meta("proposedBy", request.handledBy())
                .meta("channel", request.channel().name())
                .meta("erasureParticipants", erasureCoordinator.participantCount())
                .meta("reason", reason)
                .before(java.util.Map.of("status", request.status().name()))
                .after(java.util.Map.of("status", DsarStatus.COMPLETED.name()))
                .build());

        return new Result(request.publicRef(), subjectId, erasureCoordinator.participantCount(),
                completed.completedAt());
    }

    private static void requireSecondPerson(DsarRequest request, UUID approverId) {
        if (request.channel() == DsarChannel.SELF_SERVICE) {
            return;
        }
        UUID proposer = request.handledBy();
        if (proposer == null) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "proposerUnknown");
        }
        if (proposer.equals(approverId)) {
            throw new BusinessRuleException(
                    PrivacyErrorCode.DSAR_SELF_APPROVAL_FORBIDDEN, request.publicRef());
        }
    }

    public record Result(String publicRef, UUID subjectId, int participantsInvoked,
                         java.time.Instant completedAt) {
    }
}
