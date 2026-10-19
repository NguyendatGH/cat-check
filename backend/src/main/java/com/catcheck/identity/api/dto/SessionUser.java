package com.catcheck.identity.api.dto;

import java.util.List;
import java.util.UUID;

public record SessionUser(
        UUID id,
        String email,
        String fullName,
        String avatarUrl,
        String locale,
        String timezone,
        String status,
        String onboardingStatus,
        boolean emailVerified,
        boolean hasPassword,
        List<String> identities) {
}
