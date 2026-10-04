package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminMaintenanceRequest(
        @NotNull Boolean active,
        String until,
        @NotBlank String reason) {
}
