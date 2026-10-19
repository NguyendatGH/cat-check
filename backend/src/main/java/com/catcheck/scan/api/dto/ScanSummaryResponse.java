package com.catcheck.scan.api.dto;

import com.catcheck.scan.domain.port.ScanQueryRepository.Summary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

public record ScanSummaryResponse(
        long count,
        Map<String, Long> byClassification,
        BigDecimal median,
        BigDecimal min,
        BigDecimal max,
        long inconclusiveCount,
        long lowConfidenceCount,
        Instant firstAt,
        Instant lastAt
) {

    public static ScanSummaryResponse from(Summary s) {
        return new ScanSummaryResponse(
                s.count(), s.byClassification(), s.median(), s.min(), s.max(),
                s.inconclusiveCount(), s.lowConfidenceCount(), s.firstAt(), s.lastAt());
    }
}
