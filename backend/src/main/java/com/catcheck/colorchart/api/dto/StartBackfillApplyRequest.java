package com.catcheck.colorchart.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartBackfillApplyRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason
) {
}
