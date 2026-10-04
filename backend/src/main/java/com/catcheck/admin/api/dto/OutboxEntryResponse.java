package com.catcheck.admin.api.dto;

import com.catcheck.admin.domain.OutboxRow;
import com.catcheck.shared.security.PiiMask;

import java.time.Instant;
import java.util.UUID;

/**
 * Một bản ghi outbox — L66 {@code GET /admin/notifications/outbox}.
 *
 * <p>{@code recipientMasked}: với kênh {@code EMAIL} đây là {@code email_outbox.to_address} đã
 * mask theo p15 REQ-RBAC-01; với kênh {@code PUSH} là {@code push_subscription_id} — một UUID
 * nội bộ, không phải PII, nên giữ nguyên để tra ngược được thiết bị nào không nhận được.</p>
 */
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
