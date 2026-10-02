package com.catcheck.identity.api.dto;

import java.time.Instant;

/** B15 — trạng thái TOTP hiện tại (p8 §8.4.4 nhóm B). */
public record TotpStatusResponse(
        String status,
        Instant activatedAt,
        long recoveryCodesRemaining,
        Instant lockedUntil) {
}
