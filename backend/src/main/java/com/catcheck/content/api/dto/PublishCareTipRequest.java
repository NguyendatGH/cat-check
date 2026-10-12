package com.catcheck.content.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublishCareTipRequest(
        @NotBlank
        @Size(min = 10, max = 1000, message = "reason phải dài tối thiểu 10 ký tự")
        String reason
) {
}
