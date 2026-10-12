package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ProductLine;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateColorChartRequest(
        @NotBlank
        @Size(max = 64)
        String code,

        @NotBlank
        @Size(max = 200)
        String name,

        @NotNull
        ProductLine productLine,

        @Size(max = 64)
        String productionBatch
) {
}
