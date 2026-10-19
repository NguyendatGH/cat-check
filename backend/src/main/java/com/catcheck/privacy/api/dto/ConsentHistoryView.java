package com.catcheck.privacy.api.dto;

import java.time.Instant;

public record ConsentHistoryView(
        String purposeCode,
        String status,
        String policyVersion,
        String policyHash,
        String method,
        String uiSurface,
        Instant occurredAt
) {
}
