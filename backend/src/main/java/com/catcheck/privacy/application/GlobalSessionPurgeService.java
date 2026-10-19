package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.application.export.DsarExportJobPort;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.spi.GlobalSessionPurgePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class GlobalSessionPurgeService {

    public static final Duration TARGET_DURATION = Duration.ofMinutes(5);

    private final GlobalSessionPurgePort sessionPurgePort;
    private final SecurityIncidentService incidentService;
    private final DsarExportJobPort dsarExportJobPort;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public GlobalSessionPurgeService(
            GlobalSessionPurgePort sessionPurgePort,
            SecurityIncidentService incidentService,
            DsarExportJobPort dsarExportJobPort,
            AuditLogService auditLogService,
            Clock clock) {
        this.sessionPurgePort = sessionPurgePort;
        this.incidentService = incidentService;
        this.dsarExportJobPort = dsarExportJobPort;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public Result purgeAll(UUID actorId, String actorRole, UUID incidentId, String reason,
                           RequestEvidence evidence) {
        SecurityIncident incident = incidentService.get(incidentId);
        Instant startedAt = clock.instant();

        int downloadLinksRevoked = dsarExportJobPort.revokeAllDownloadLinks(startedAt);
        GlobalSessionPurgePort.PurgeOutcome outcome = sessionPurgePort.purgeEverySession();

        Instant finishedAt = clock.instant();
        auditLogService.record(AuditEvent.builder()
                .actor(AuditActor.admin(actorId, actorRole))
                .subject(AuditSubjectType.SYSTEM, null)
                .action("SECURITY.SESSIONS_PURGED_ALL")
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("incidentId", incident.id())
                .meta("incidentRef", incident.publicRef())
                .meta("reason", reason)
                .meta("revokedCount", outcome.httpSessionsDeleted())
                .meta("deviceSessionsRevoked", outcome.deviceSessionsRevoked())
                .meta("otpInvalidated", outcome.otpInvalidated())
                .meta("dsarDownloadLinksRevoked", downloadLinksRevoked)
                .meta("elapsedMillis", Duration.between(startedAt, finishedAt).toMillis())
                .build());

        return new Result(incident.publicRef(), outcome.httpSessionsDeleted(),
                outcome.deviceSessionsRevoked(), outcome.otpInvalidated(), downloadLinksRevoked,
                Duration.between(startedAt, finishedAt).toMillis());
    }

    public record Result(String incidentRef, int revokedCount, int deviceSessionsRevoked,
                         int otpInvalidated, int dsarDownloadLinksRevoked, long elapsedMillis) {
    }
}
