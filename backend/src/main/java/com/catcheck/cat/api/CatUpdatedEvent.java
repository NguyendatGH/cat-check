package com.catcheck.cat.api;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record CatUpdatedEvent(
        UUID catId,
        UUID ownerId,
        Set<String> changedFields,
        String previousStatus,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_UPDATED";
    }
}
