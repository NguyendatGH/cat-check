package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record PublishPolicyVersionRequest(
        Instant effectiveFrom,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
