package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.AuditLogRow;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        Instant occurredAt,
        String actorType,
        String actorRole,
        String subjectType,
        String action,
        String result,
        String requestId,
        String metadata) {

    public static AuditLogResponse from(AuditLogRow row) {
        return new AuditLogResponse(row.id(), row.occurredAt(), row.actorType(), row.actorRole(),
                row.subjectType(), row.action(), row.result(), row.requestId(), row.metadata());
    }
}
