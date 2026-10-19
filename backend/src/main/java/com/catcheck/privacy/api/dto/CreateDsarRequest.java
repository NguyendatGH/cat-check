package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateDsarRequest(
        @NotBlank(message = "{validation.privacy.requestType.required}")
        @Pattern(regexp = "[A-Z_]{1,24}", message = "{validation.privacy.requestType.invalid}")
        String requestType,

        @Pattern(regexp = "[A-Z_]{1,16}", message = "{validation.privacy.channel.invalid}")
        String channel
) {
}
