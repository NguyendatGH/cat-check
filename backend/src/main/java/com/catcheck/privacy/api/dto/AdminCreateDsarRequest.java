package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AdminCreateDsarRequest(
        @NotNull UUID userId,
        @NotBlank String requestType,
        @NotBlank String channel,
        @NotBlank String reason) {
}
