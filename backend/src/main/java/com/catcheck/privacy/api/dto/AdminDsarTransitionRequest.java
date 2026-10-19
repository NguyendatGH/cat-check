package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;

public record AdminDsarTransitionRequest(
        @NotBlank String action,
        String reason,
        Instant extendedTo) {
}
