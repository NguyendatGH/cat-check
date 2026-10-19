package com.catcheck.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishCareTipRequest(
        @NotBlank
        @Size(min = 10, max = 1000, message = "{validation.content.reason.min}")
        String reason
) {
}
