package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAppSettingRequest(
        @NotNull @Size(max = 10_000) String value,
        @NotBlank @Size(max = 500) String reason) {
}
