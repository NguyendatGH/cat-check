package com.catcheck.scan.domain;

import java.math.BigDecimal;

public record ScanRecomputeImpact(
        long evaluated,
        long flipped,
        BigDecimal maxAbsDeltaPh,
        java.util.UUID jobId
) {

    public static ScanRecomputeImpact empty() {
        return new ScanRecomputeImpact(0L, 0L, null, null);
    }
}
