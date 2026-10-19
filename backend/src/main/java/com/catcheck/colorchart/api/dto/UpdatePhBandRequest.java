package com.catcheck.colorchart.api.dto;

import com.catcheck.colorchart.domain.BandSeverity;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

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
