package com.catcheck.identity.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
