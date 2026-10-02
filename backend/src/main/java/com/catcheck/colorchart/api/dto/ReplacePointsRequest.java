package com.catcheck.colorchart.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Body của L31 — thay toàn bộ danh sách mức pH.
 */
public record ReplacePointsRequest(
        @NotEmpty
        List<@Valid PointInput> points
) {
}
