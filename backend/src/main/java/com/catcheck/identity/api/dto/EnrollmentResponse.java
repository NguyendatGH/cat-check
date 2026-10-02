package com.catcheck.identity.api.dto;

import java.time.Instant;

/** B15 — khởi tạo đăng ký TOTP (p8 §8.4.4 nhóm B). */
public record EnrollmentResponse(
        String secretBase32,
        String otpauthUri,
        Instant expiresAt,
        int digits,
        int periodSeconds,
        String algorithm) {
}
