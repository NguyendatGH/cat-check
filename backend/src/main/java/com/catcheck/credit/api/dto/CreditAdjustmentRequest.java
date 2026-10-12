package com.catcheck.credit.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreditAdjustmentRequest(
        @NotBlank @Pattern(regexp = "GRANT|REVOKE") String direction,
        @NotNull @Min(1) @Max(100_000) Integer amount,
        @Size(max = 32) String packageCode,
        @Min(1) @Max(3_650) Integer validityDays,
        @NotBlank @Size(min = 10, max = 500) String reason
) {
}
