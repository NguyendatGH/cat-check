package com.catcheck.credit.api.dto;

import java.time.Instant;

public record ActivationResponse(
        String packageCode,
        String packageName,
        int creditsGranted,
        Instant expiresAt,
        int balanceAfter
) {
}
