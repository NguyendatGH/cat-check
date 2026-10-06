package com.catcheck.privacy.api.dto;

import com.catcheck.privacy.application.SecurityIncidentService;
import com.catcheck.privacy.domain.SecurityIncident;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ sự cố cho L59/L60/L61 (p4 B9, p15 REQ-INC-01).
 *
 * <p>{@code authorityDeadline} là {@code detectedAt + 72 giờ} (Điều 23.1 Luật BVDLCN) — trả
 * sẵn để UI đếm ngược không phải tự cộng, và {@code authorityNotificationOverdue} là đúng
 * điều kiện mà {@code IncidentDeadlineMonitorJob} dùng (REQ-INC-02), nên màn admin và job
 * cảnh báo không bao giờ nói hai điều khác nhau.</p>
 */
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
