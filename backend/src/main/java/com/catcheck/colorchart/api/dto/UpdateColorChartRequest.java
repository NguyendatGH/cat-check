package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ProductLine;
import jakarta.validation.constraints.Size;

public record UpdateColorChartRequest(
        @Size(max = 200)
        String name,

        ProductLine productLine,

        @Size(max = 64)
        String productionBatch
) {
}
