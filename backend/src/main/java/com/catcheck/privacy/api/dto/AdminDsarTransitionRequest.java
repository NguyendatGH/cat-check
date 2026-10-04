package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

/** Payload L51 — một endpoint cho các thao tác xử lý DSAR. */
public record AdminDsarTransitionRequest(
        @NotBlank String action,
        String reason,
        Instant extendedTo) {
}
