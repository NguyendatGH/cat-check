package com.catcheck.cat.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record ClinicalSignsReportedEvent(
        UUID reportId,
        UUID catId,
        UUID ownerId,
        Set<String> signs,
        String source,
        boolean blockingShown,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CLINICAL_SIGNS_REPORTED";
    }
}
