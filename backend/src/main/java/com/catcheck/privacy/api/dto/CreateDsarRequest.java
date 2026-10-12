package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateDsarRequest(
        @NotBlank(message = "requestType không được trống")
        @Pattern(regexp = "[A-Z_]{1,24}", message = "requestType không hợp lệ")
        String requestType,

        @Pattern(regexp = "[A-Z_]{1,16}", message = "channel không hợp lệ")
        String channel
) {
}
