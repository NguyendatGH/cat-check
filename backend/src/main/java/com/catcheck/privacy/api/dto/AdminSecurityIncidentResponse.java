package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.SecurityIncidentService;
import com.catcheck.privacy.domain.SecurityIncident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminSecurityIncidentResponse(
        UUID id,
        String publicRef,
        String severity,
        String category,
        String summary,
        Integer affectedSubjectCount,
        List<String> affectedDataCodes,
        Instant detectedAt,
        Instant classifiedAt,
        Instant containedAt,
        Instant authorityNotifiedAt,
        Instant subjectsNotifiedAt,
        Instant resolvedAt,
        Instant retainUntil,
        UUID handledBy,
        String reportRef,
        Instant authorityDeadline,
        Boolean authorityNotificationOverdue,
        Instant createdAt
) {

    public static AdminSecurityIncidentResponse from(SecurityIncident incident, Instant now) {
        return new AdminSecurityIncidentResponse(
                incident.id(), incident.publicRef(), incident.severity().name(),
                incident.category().name(), incident.summary(), incident.affectedSubjectCount(),
                incident.affectedDataCodes(), incident.detectedAt(), incident.classifiedAt(),
                incident.containedAt(), incident.authorityNotifiedAt(), incident.subjectsNotifiedAt(),
                incident.resolvedAt(), incident.retainUntil(), incident.handledBy(),
                incident.reportRef(), SecurityIncidentService.authorityDeadline(incident),
                incident.isAuthorityNotificationOverdue(now), incident.createdAt());
    }
}
