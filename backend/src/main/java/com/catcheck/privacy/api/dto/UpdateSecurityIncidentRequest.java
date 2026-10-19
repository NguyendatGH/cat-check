package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record UpdateSecurityIncidentRequest(
        String severity,
        String category,
        @Size(max = 4000) String summary,
        @PositiveOrZero Integer affectedSubjectCount,
        List<@Size(max = 8) String> affectedDataCodes,
        Instant classifiedAt,
        Instant containedAt,
        Instant authorityNotifiedAt,
        Instant subjectsNotifiedAt,
        Instant resolvedAt,
        @Size(max = 500) String reportRef,
        @NotNull String reason) {
}
