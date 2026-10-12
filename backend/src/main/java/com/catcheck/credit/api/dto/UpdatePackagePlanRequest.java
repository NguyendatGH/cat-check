package com.catcheck.credit.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePackagePlanRequest(
        @Min(1) @Max(100_000) Integer creditAmount,
        @Min(1) @Max(3_650) Integer creditValidityDays,
        @Min(1) @Max(1_000) Integer maxCatProfiles,
        Boolean clearMaxCatProfiles,
        @Valid PlanFeaturesView features,
        Boolean active,
        @NotBlank @Size(min = 10, max = 500) String reason
) {

    public boolean clearMaxCatProfilesFlag() {
        return Boolean.TRUE.equals(clearMaxCatProfiles);
    }
}
