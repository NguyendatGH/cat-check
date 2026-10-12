package com.catcheck.cat.api;

import java.time.Instant;
import java.util.UUID;

public record CatArchivedEvent(
        UUID catId,
        UUID ownerId,
        boolean softDelete,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_DELETED";
    }
}
