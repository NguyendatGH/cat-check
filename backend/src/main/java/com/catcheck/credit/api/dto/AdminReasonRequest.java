package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReasonRequest(@NotBlank @Size(min = 10, max = 500) String reason) {
}
