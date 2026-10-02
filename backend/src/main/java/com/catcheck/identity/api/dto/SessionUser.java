package com.catcheck.identity.api.dto;

import java.util.List;
import java.util.UUID;

/** Thông tin user lồng trong {@link SessionResponse} (p8 §8.4.4 nhóm A). */
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
