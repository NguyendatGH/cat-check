package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AcknowledgePolicyRequest(
        @NotBlank(message = "surface không được trống")
        @Pattern(regexp = "[a-z_]{1,64}", message = "surface không hợp lệ")
        String surface
) {
}
