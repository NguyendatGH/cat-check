package com.catcheck.colorchart.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ReplacePointsRequest(
        @NotEmpty
        List<@Valid PointInput> points
) {
}
