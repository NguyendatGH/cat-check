package com.catcheck.content.api.dto;

import com.catcheck.content.domain.CareTipCategory;
import com.catcheck.content.domain.CareTipKind;
import com.catcheck.content.domain.ClaimType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateCareTipRequest(
        @NotBlank
        @Size(max = 120)
        @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*", message = "slug phải là kebab-case chữ thường")
        String slug,

        @NotBlank
        @Pattern(regexp = "vi|en", message = "locale chỉ nhận 'vi' hoặc 'en'")
        String locale,

        @NotNull CareTipKind kind,
        CareTipCategory category,

        @NotBlank
        @Size(max = 200)
        String title,

        @NotNull ClaimType claimType,

        List<@Size(max = 40) String> tags
) {
}
