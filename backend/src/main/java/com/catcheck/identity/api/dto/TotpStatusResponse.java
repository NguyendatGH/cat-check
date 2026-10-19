package com.catcheck.identity.api.dto;

import java.time.Instant;

public record TotpStatusResponse(
        String status,
        Instant activatedAt,
        long recoveryCodesRemaining,
        Instant lockedUntil) {
}
