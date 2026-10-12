package com.catcheck.admin.application;

import com.catcheck.admin.domain.JobRunRow;
import com.catcheck.admin.domain.AuditLogRow;
import com.catcheck.admin.domain.AdminMetrics;
import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.admin.domain.port.AdminMetricsQueryPort;
import com.catcheck.admin.domain.port.AuditLogQueryPort;
import com.catcheck.admin.domain.port.OpsQueryPort;
import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditOutcome;
import com.catcheck.audit.api.AuditSubjectType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AdminOpsQueryService {

    private final OpsQueryPort opsQueryPort;
    private final AuditLogQueryPort auditLogQueryPort;
    private final AdminMetricsQueryPort adminMetricsQueryPort;
    private final AuditLogService auditLogService;

    public AdminOpsQueryService(OpsQueryPort opsQueryPort, AuditLogQueryPort auditLogQueryPort,
                                AdminMetricsQueryPort adminMetricsQueryPort, AuditLogService auditLogService) {
        this.opsQueryPort = opsQueryPort;
        this.auditLogQueryPort = auditLogQueryPort;
        this.adminMetricsQueryPort = adminMetricsQueryPort;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public AdminMetrics metrics() {
        return adminMetricsQueryPort.read();
    }

    @Transactional(readOnly = true)
    public Page<AuditLogRow> auditLogs(String action, String result, String actorType, UUID actorId, int page, int size) {
        return new Page<>(auditLogQueryPort.find(action, result, actorType, actorId, page * size, size),
                auditLogQueryPort.count(action, result, actorType, actorId));
    }

    @Transactional(readOnly = true)
    public Page<JobRunRow> jobRuns(String jobName, String status, int page, int size) {
        return new Page<>(
                opsQueryPort.findJobRuns(jobName, status, page * size, size),
                opsQueryPort.countJobRuns(jobName, status));
    }

    @Transactional
    public Page<OutboxRow> outbox(String channel, String status, int page, int size,
                                  UUID adminId, String adminRole, String requestId,
                                  String ipAddress, String userAgent) {
        List<OutboxRow> items = opsQueryPort.findOutbox(channel, status, page * size, size);
        long total = opsQueryPort.countOutbox(channel, status);

        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(adminId, adminRole))
                .subject(AuditSubjectType.NOTIFICATION, null)
                .action("ADMIN_NOTIFICATION_OUTBOX_VIEW")
                .outcome(AuditOutcome.SUCCESS)
                .meta("channel", channel)
                .meta("status", status)
                .meta("resultCount", items.size())
                .requestId(requestId)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build());
        return new Page<>(items, total);
    }

    public record Page<T>(List<T> items, long totalElements) {

        public Page {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }
}
