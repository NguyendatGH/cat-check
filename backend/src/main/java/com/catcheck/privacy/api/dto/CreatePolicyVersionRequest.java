package com.catcheck.privacy.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public record CreatePolicyVersionRequest(
        @NotBlank String policyType,

        @NotBlank
        @Pattern(regexp = "^\\d{1,4}\\.\\d{1,4}(\\.\\d{1,4})?$",
                message = "version phải là semver MAJOR.MINOR[.PATCH]")
        String version,

        @Pattern(regexp = "^(vi|en)$", message = "locale chỉ nhận vi hoặc en") String locale,

        @NotBlank @Size(max = 300) String title,

        String contentMd,

        String contentUrl,

        @Size(max = 4000) String summaryOfChanges,

        @NotNull Boolean requiresReconsent,

        List<String> affectedPurposes,

        @NotNull Instant effectiveFrom,

        @NotBlank @Size(min = 10, max = 500) String reason
) {

    public String localeOrDefault() {
        return locale == null || locale.isBlank() ? "vi" : locale;
    }
}
