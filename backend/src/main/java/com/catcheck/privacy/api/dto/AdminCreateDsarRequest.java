package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Payload L54 — tạo DSAR qua kênh ngoài self-service thay mặt một user. */
public record AdminCreateDsarRequest(
        @NotNull UUID userId,
        @NotBlank String requestType,
        @NotBlank String channel,
        @NotBlank String reason) {
}
