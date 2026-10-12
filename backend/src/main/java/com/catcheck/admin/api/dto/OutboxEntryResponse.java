package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

public record OutboxEntryResponse(
        UUID id,
        String channel,
        String status,
        String reference,
        String recipientMasked,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant sentAt,
        Instant createdAt
) {

    public static OutboxEntryResponse from(OutboxRow row) {
        return new OutboxEntryResponse(
                row.id(),
                row.channel(),
                row.status(),
                row.reference(),
                row.recipientIsEmail() ? PiiMask.email(row.recipient()) : row.recipient(),
                row.attempts(),
                row.nextAttemptAt(),
                row.lastError(),
                row.sentAt(),
                row.createdAt());
    }
}
