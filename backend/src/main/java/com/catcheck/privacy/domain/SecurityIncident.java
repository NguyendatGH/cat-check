package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SecurityIncident(
        UUID id,
        String publicRef,
        IncidentSeverity severity,
        IncidentCategory category,
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
        Instant createdAt
) {

    public SecurityIncident {
        if (id == null || publicRef == null || severity == null || category == null || summary == null) {
            throw new IllegalArgumentException("securityIncident thiếu trường bắt buộc");
        }
        if (detectedAt == null) {
            throw new IllegalArgumentException("securityIncident.detectedAt bắt buộc (đồng hồ 72 giờ)");
        }
        affectedDataCodes = affectedDataCodes == null ? List.of() : List.copyOf(affectedDataCodes);
    }

    public boolean isAuthorityNotificationOverdue(Instant now) {
        boolean highSeverity = severity == IncidentSeverity.HIGH || severity == IncidentSeverity.CRITICAL;
        return highSeverity && authorityNotifiedAt == null
                && detectedAt.plusSeconds(72 * 3600).isBefore(now);
    }
}
