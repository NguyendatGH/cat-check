package com.catcheck.audit.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditAccessLogQuery {

    List<Entry> accessesToUserData(UUID subjectUserId, int limit);

    record Entry(Instant occurredAt, String actorType, String actorRole, String action, String result) {
    }
}
