package com.catcheck.privacy.application;

import com.catcheck.audit.api.AuditActor;
import com.catcheck.audit.api.AuditEvent;
import com.catcheck.audit.api.AuditLogService;
import com.catcheck.audit.api.AuditSubjectType;
import com.catcheck.privacy.api.PrivacyErrorCode;
import com.catcheck.privacy.domain.IncidentCategory;
import com.catcheck.privacy.domain.IncidentSeverity;
import com.catcheck.privacy.domain.SecurityIncident;
import com.catcheck.privacy.domain.port.SecurityIncidentPort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.id.UuidV7;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SecurityIncidentService {

    public static final int RETAIN_YEARS_AFTER_RESOLVED = 5;

    public static final long AUTHORITY_NOTIFICATION_HOURS = 72;

    private static final int MAX_SUMMARY_LENGTH = 4000;

    private final SecurityIncidentPort incidentPort;
    private final AuditLogService auditLogService;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public SecurityIncidentService(SecurityIncidentPort incidentPort, AuditLogService auditLogService,
                                   UuidV7 uuidV7, Clock clock) {
        this.incidentPort = incidentPort;
        this.auditLogService = auditLogService;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public SecurityIncident openIncident(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes
    ) {
        return incidentPort.save(new SecurityIncident(
                uuidV7.generate(),
                incidentPort.nextPublicRef(),
                severity,
                category,
                summary,
                affectedSubjectCount,
                affectedDataCodes,
                clock.instant(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                clock.instant()));
    }

    @Transactional
    public SecurityIncident open(UUID actorId, Command command, RequestEvidence evidence) {
        Instant now = clock.instant();
        Instant detectedAt = command.detectedAt() == null ? now : command.detectedAt();
        if (detectedAt.isAfter(now)) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "detectedAt");
        }
        String summary = requireSummary(command.summary());
        SecurityIncident saved = incidentPort.save(new SecurityIncident(
                uuidV7.generate(), "PENDING", command.severity(), command.category(), summary,
                command.affectedSubjectCount(), command.affectedDataCodes(), detectedAt,
                null, null, null, null, null, null, actorId, command.reportRef(), now));
        auditLogService.record(audit(actorId, saved, "SECURITY_INCIDENT_OPENED", command.reason(), evidence)
                .meta("severity", saved.severity().name())
                .meta("category", saved.category().name())
                .build());
        return saved;
    }

    @Transactional
    public SecurityIncident patch(UUID actorId, UUID incidentId, Patch patch, RequestEvidence evidence) {
        SecurityIncident current = incidentPort.findById(incidentId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND));
        Instant now = clock.instant();

        Instant classifiedAt = mergeMilestone(current.classifiedAt(), patch.classifiedAt(), current, now, "classifiedAt");
        Instant containedAt = mergeMilestone(current.containedAt(), patch.containedAt(), current, now, "containedAt");
        Instant authorityNotifiedAt = mergeMilestone(current.authorityNotifiedAt(),
                patch.authorityNotifiedAt(), current, now, "authorityNotifiedAt");
        Instant subjectsNotifiedAt = mergeMilestone(current.subjectsNotifiedAt(),
                patch.subjectsNotifiedAt(), current, now, "subjectsNotifiedAt");
        Instant resolvedAt = mergeMilestone(current.resolvedAt(), patch.resolvedAt(), current, now, "resolvedAt");

        SecurityIncident updated = new SecurityIncident(
                current.id(),
                current.publicRef(),
                patch.severity() == null ? current.severity() : patch.severity(),
                patch.category() == null ? current.category() : patch.category(),
                patch.summary() == null ? current.summary() : requireSummary(patch.summary()),
                patch.affectedSubjectCount() == null
                        ? current.affectedSubjectCount() : patch.affectedSubjectCount(),
                patch.affectedDataCodes() == null
                        ? current.affectedDataCodes() : patch.affectedDataCodes(),
                current.detectedAt(),
                classifiedAt,
                containedAt,
                authorityNotifiedAt,
                subjectsNotifiedAt,
                resolvedAt,
                resolvedAt == null ? current.retainUntil()
                        : resolvedAt.plus(RETAIN_YEARS_AFTER_RESOLVED * 365L, ChronoUnit.DAYS),
                actorId,
                patch.reportRef() == null ? current.reportRef() : patch.reportRef(),
                current.createdAt());
        incidentPort.update(updated);

        auditLogService.record(audit(actorId, updated, "SECURITY_INCIDENT_UPDATED", patch.reason(), evidence)
                .before(milestones(current))
                .after(milestones(updated))
                .build());
        return updated;
    }

    public List<SecurityIncident> list(IncidentSeverity severity, Boolean unresolved, int offset, int limit) {
        return incidentPort.findForAdmin(severity, unresolved, offset, limit);
    }

    public long count(IncidentSeverity severity, Boolean unresolved) {
        return incidentPort.countForAdmin(severity, unresolved);
    }

    public SecurityIncident get(UUID incidentId) {
        return incidentPort.findById(incidentId)
                .orElseThrow(() -> new BusinessRuleException(PrivacyErrorCode.SECURITY_INCIDENT_NOT_FOUND));
    }

    @Transactional
    public void updateIncident(SecurityIncident incident) {
        incidentPort.update(incident);
    }

    public List<SecurityIncident> findOverdueAuthorityNotifications() {
        return incidentPort.findOverdueAuthorityNotification(clock.instant());
    }

    public static Instant authorityDeadline(SecurityIncident incident) {
        return incident.detectedAt().plusSeconds(AUTHORITY_NOTIFICATION_HOURS * 3600);
    }

    private static Instant mergeMilestone(Instant current, Instant requested, SecurityIncident incident,
                                          Instant now, String field) {
        if (requested == null) {
            return current;
        }
        if (requested.isAfter(now) || requested.isBefore(incident.detectedAt())) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, field);
        }
        return requested;
    }

    private static String requireSummary(String summary) {
        String trimmed = summary == null ? "" : summary.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_SUMMARY_LENGTH) {
            throw new BusinessRuleException(PrivacyErrorCode.VALIDATION_FAILED, "summary");
        }
        return trimmed;
    }

    private static AuditEvent.Builder audit(UUID actorId, SecurityIncident incident, String action,
                                            String reason, RequestEvidence evidence) {
        AuditEvent.Builder builder = AuditEvent.builder()
                .actor(AuditActor.dpo(actorId, AdminErasureApprovalService.ROLE_DPO))
                .subject(AuditSubjectType.SYSTEM, null)
                .action(action)
                .requestId(evidence.requestId())
                .ipAddress(evidence.ipAddress())
                .userAgent(evidence.userAgent())
                .meta("incidentId", incident.id())
                .meta("publicRef", incident.publicRef());
        if (reason != null && !reason.isBlank()) {
            builder.meta("reason", reason.strip());
        }
        return builder;
    }

    private static java.util.Map<String, Object> milestones(SecurityIncident incident) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<>();
        values.put("severity", incident.severity().name());
        values.put("category", incident.category().name());
        values.put("classifiedAt", String.valueOf(incident.classifiedAt()));
        values.put("containedAt", String.valueOf(incident.containedAt()));
        values.put("authorityNotifiedAt", String.valueOf(incident.authorityNotifiedAt()));
        values.put("subjectsNotifiedAt", String.valueOf(incident.subjectsNotifiedAt()));
        values.put("resolvedAt", String.valueOf(incident.resolvedAt()));
        values.put("retainUntil", String.valueOf(incident.retainUntil()));
        return values;
    }

    public record Command(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes,
            Instant detectedAt,
            String reportRef,
            String reason) {
    }

    public record Patch(
            IncidentSeverity severity,
            IncidentCategory category,
            String summary,
            Integer affectedSubjectCount,
            List<String> affectedDataCodes,
            Instant classifiedAt,
            Instant containedAt,
            Instant authorityNotifiedAt,
            Instant subjectsNotifiedAt,
            Instant resolvedAt,
            String reportRef,
            String reason) {
    }
}
