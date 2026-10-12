package com.catcheck.privacy.spi;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserAccountSnapshot(
        UUID userId,
        String email,
        String status,
        Instant processingRestrictedAt,
        Instant deletionScheduledAt,
        Instant anonymizedAt,
        UUID pseudonymId,
        Set<String> roles
) {

    public UserAccountSnapshot {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
    }

    public boolean isProcessingRestricted() {
        return processingRestrictedAt != null;
    }

    public boolean isDeletionRequested() {
        return deletionScheduledAt != null;
    }

    public boolean isAnonymized() {
        return anonymizedAt != null;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }
}
