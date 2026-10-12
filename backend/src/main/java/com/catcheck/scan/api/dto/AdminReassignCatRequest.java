package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminReassignCatRequest(
        String toCatId,
        String toAssignment,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
