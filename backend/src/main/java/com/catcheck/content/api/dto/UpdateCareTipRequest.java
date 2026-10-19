package com.catcheck.content.api.dto;

import com.catcheck.content.domain.ClaimType;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateCareTipRequest(
        @Size(min = 1, max = 200)
        String title,

        String summary,
        String bodyMd,
        ClaimType claimType,
        String sourceReference,

        @Size(min = 0, max = 10_000)
        Integer sortWeight,

        List<@Size(max = 40) String> tags
) {
}
