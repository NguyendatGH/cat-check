package com.catcheck.admin.domain;

import java.time.Instant;
import java.util.UUID;

public record OutboxRow(
        UUID id,
        String channel,
        String status,
        String reference,
        String recipient,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant sentAt,
        Instant createdAt
) {

    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_PUSH = "PUSH";

    public boolean recipientIsEmail() {
        return CHANNEL_EMAIL.equals(channel);
    }
}
