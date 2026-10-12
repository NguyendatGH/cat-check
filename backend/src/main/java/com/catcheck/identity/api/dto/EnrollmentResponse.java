package com.catcheck.identity.api.dto;

import java.time.Instant;

public record EnrollmentResponse(
        String secretBase32,
        String otpauthUri,
        Instant expiresAt,
        int digits,
        int periodSeconds,
        String algorithm) {
}
