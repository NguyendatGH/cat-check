package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConsentGrantRequest(
        @NotBlank(message = "purposeCode không được trống")
        @Pattern(regexp = "[A-Z_]{1,48}", message = "purposeCode không hợp lệ")
        String purposeCode,
        boolean granted
) {
}
