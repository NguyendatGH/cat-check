package com.catcheck.admin.application;

import com.catcheck.admin.api.AdminOpsErrorCode;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.notification.api.OutboxAdminGateway;
import com.catcheck.notification.api.SystemBroadcastGateway;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ConflictException;
import com.catcheck.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AdminNotificationOpsService {

    private final OutboxAdminGateway outboxAdmin;
    private final SystemBroadcastGateway broadcast;
    private final AuditLogService auditLogService;

    public AdminNotificationOpsService(OutboxAdminGateway outboxAdmin,
                                       SystemBroadcastGateway broadcast,
                                       AuditLogService auditLogService) {
        this.outboxAdmin = outboxAdmin;
        this.broadcast = broadcast;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public void resendOutboxEntry(UUID outboxId, UUID adminId, String adminRole,
                                  String requestId, String ipAddress, String userAgent) {
        OutboxAdminGateway.ResendOutcome outcome = outboxAdmin.resendFailed(outboxId);
        switch (outcome) {
            case NOT_FOUND -> throw new NotFoundException(
                    AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FOUND, outboxId);
            case NOT_FAILED -> throw new ConflictException(
                    AdminOpsErrorCode.OUTBOX_ENTRY_NOT_FAILED, outboxId);
            case REQUEUED -> auditLogService.record(AuditEvent.builder()
                    .actor(AuditActor.admin(adminId, adminRole))
                    .subject(AuditSubjectType.SYSTEM, null)
                    .action("ADMIN_OUTBOX_RESEND")
                    .meta("outboxId", outboxId)
                    .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                    .build());
        }
    }

    @Transactional
    public SystemBroadcastGateway.BroadcastResult broadcast(
            String templateCode, String title, String body, Map<String, Object> payload,
            boolean dryRun, UUID adminId, String adminRole, String reason,
            String requestId, String ipAddress, String userAgent) {
        if (!SystemBroadcastGateway.ALLOWED_TEMPLATE_CODES.contains(templateCode)) {
            throw new BusinessRuleException(
                    AdminOpsErrorCode.BROADCAST_TEMPLATE_NOT_ALLOWED,
                    String.join(" | ", SystemBroadcastGateway.ALLOWED_TEMPLATE_CODES
                            .stream().sorted().toList()));
        }
        SystemBroadcastGateway.BroadcastResult result =
                broadcast.broadcast(templateCode, title, body, payload, dryRun);
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("ADMIN_SYSTEM_BROADCAST")
                .requestId(requestId).ipAddress(ipAddress).userAgent(userAgent)
                .meta("templateCode", templateCode)
                .meta("dryRun", dryRun)
                .meta("recipientsMatched", result.recipientsMatched())
                .meta("notificationsQueued", result.notificationsQueued())
                .meta("dedupeKeyPrefix", result.dedupeKeyPrefix())
                .meta("reason", reason)
                .build());
        return result;
    }
}
