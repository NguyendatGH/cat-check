package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record IssueActivationBatchRequest(
        @NotBlank @Size(max = 32) @Pattern(regexp = "[A-Z0-9_]{1,32}") String packageCode,
        @NotNull @Min(1) @Max(50_000) Integer quantity,
        @NotBlank @Size(max = 64) String productionBatch,
        @NotNull @Min(1) @Max(3_650) Integer validForDays,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
