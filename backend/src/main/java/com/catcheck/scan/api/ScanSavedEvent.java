package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ScanSavedEvent(
        UUID scanId,
        UUID catId,
        UUID userId,
        String assignment,
        BigDecimal phValue,
        String classification,
        BigDecimal confidence,
        boolean nearBoundary,
        String calibrationMethod,
        Instant capturedAt,
        boolean chartIsPlaceholder
) {
}
