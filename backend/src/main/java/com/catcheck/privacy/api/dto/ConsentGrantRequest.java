package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsentGrantRequest(
        @NotBlank(message = "{validation.privacy.purposeCode.required}")
        @Pattern(regexp = "[A-Z_]{1,48}", message = "{validation.privacy.purposeCode.invalid}")
        String purposeCode,
        boolean granted
) {
}
