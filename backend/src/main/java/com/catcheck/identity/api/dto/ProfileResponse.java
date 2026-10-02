package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** B1 — hồ sơ tài khoản đầy đủ (p8 §8.4.4 nhóm B). */
public record ProfileResponse(
        UUID id,
        String email,
        String fullName,
        String phone,
        String locale,
        String timezone,
        String status,
        String onboardingStatus,
        boolean emailVerified,
        List<String> identities,
        Instant createdAt) {
}
