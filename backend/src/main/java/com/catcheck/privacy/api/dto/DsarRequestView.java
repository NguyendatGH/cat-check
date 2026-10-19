package com.catcheck.privacy.api.dto;

import java.time.Instant;

public record DsarRequestView(
        String publicRef,
        String requestType,
        String channel,
        String status,
        Instant receivedAt,
        Instant ackDueAt,
        Instant fulfilDueAt
) {
}
