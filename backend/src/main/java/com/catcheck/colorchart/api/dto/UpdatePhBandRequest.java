package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.BandSeverity;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body của L37 — sửa một dải phân loại.
 *
 * <p>Merge-patch: vắng field = không đổi. Không có trường {@code code} — đổi code phá vỡ lịch sử
 * phân loại đã lưu trong {@code scan_analysis}.
 */
public record UpdatePhBandRequest(
        BigDecimal minPh,
        BigDecimal maxPh,
        Boolean minInclusive,
        Boolean maxInclusive,
        @Size(max = 200)
        String labelVi,
        @Size(max = 200)
        String labelEn,
        @Size(max = 500)
        String descriptionVi,
        @Size(max = 500)
        String descriptionEn,
        BandSeverity severity,
        @Size(max = 48)
        String colorToken,
        @Size(max = 48)
        String iconName,
        Boolean triggersAlert,
        Integer sortOrder,
        Boolean active
) {
}
