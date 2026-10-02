package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.ProductLine;
import jakarta.validation.constraints.Size;

/**
 * Body của L30 — sửa metadata bản {@code DRAFT}.
 *
 * <p>Merge-patch (RFC 7396): vắng field = không đổi; {@code null} tường minh = xoá về null.
 */
public record UpdateColorChartRequest(
        @Size(max = 200)
        String name,

        ProductLine productLine,

        @Size(max = 64)
        String productionBatch
) {
}
