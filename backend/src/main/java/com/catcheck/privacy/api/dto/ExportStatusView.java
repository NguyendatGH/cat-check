package com.catcheck.privacy.api.dto;

import java.time.Instant;

public record ExportStatusView(
        String publicRef,
        String status,
        Instant ackDueAt,
        Instant fulfilDueAt,
        Instant resultExpiresAt,
        Instant completedAt,
        String downloadToken
) {
}
