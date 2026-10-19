package com.catcheck.scan.api.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DevManualScanRequest(
        String catId,
        String assignment,
        @NotNull(message = "{validation.scan.capturedAt.required}") Instant capturedAt,
        @DecimalMin(value = "3.0", message = "{validation.scan.ph.min}")
        @DecimalMax(value = "11.0", message = "{validation.scan.ph.max}")
        BigDecimal phValue,
        String classification,
        @DecimalMin(value = "0.0") @DecimalMax(value = "1.0") BigDecimal confidence,
        List<String> qualityFlags,
        String captureSource,
        @Size(max = 120) String deviceHint
) {
}
