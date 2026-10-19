package com.catcheck.cat.api;

import java.time.Instant;
import java.util.UUID;

public record CatCreatedEvent(
        UUID catId,
        UUID ownerId,
        String publicCode,
        boolean isFirstCat,
        boolean isPrimary,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_CREATED";
    }
}
