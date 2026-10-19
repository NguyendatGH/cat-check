package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AcknowledgePolicyRequest(
        @NotBlank(message = "{validation.privacy.surface.required}")
        @Pattern(regexp = "[a-z_]{1,64}", message = "{validation.privacy.surface.invalid}")
        String surface
) {
}
