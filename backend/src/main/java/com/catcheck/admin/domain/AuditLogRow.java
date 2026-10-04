package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

/** Dữ liệu audit đã loại bỏ các cột before/after có thể chứa nội dung nhạy cảm. */
public record AuditLogRow(
        UUID id,
        Instant occurredAt,
        String actorType,
        String actorRole,
        String subjectType,
        String action,
        String result,
        String requestId,
        String metadata) {
}
