package com.catcheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RunJobRequest(
        Boolean dryRun,
        @NotBlank @Size(max = 500) String reason) {

    public boolean dryRunOrFalse() {
        return Boolean.TRUE.equals(dryRun);
    }
}
