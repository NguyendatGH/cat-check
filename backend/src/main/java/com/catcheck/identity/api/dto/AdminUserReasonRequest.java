package com.catcheck.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
