package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminRoleRequest(@NotBlank String role, @NotBlank String reason) {
}
