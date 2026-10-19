package com.catcheck.cat.application.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScanInsightPort {

    Optional<LastScan> lastScanOf(UUID catId);

    java.util.Map<UUID, LastScan> lastScansOf(java.util.Collection<UUID> catIds);

    java.util.Map<UUID, Long> unacknowledgedFlagCounts(java.util.Collection<UUID> catIds);

    WindowStats windowStats(UUID catId, Instant from, Instant to);

    long unacknowledgedFlagCount(UUID catId);

    List<TrendPoint> trendPoints(UUID catId, Instant from, Instant to);

    List<PhBandView> activeBands();

    List<Integer> chartVersionsIn(UUID catId, Instant from, Instant to);

    record LastScan(
            UUID scanId,
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence) {
    }

    record WindowStats(long scanCount, long inRangeCount, long conclusiveCount) {
    }

    record TrendPoint(
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            boolean nearBoundary,
            boolean disputed) {
    }

    record PhBandView(
            String code,
            BigDecimal minPh,
            BigDecimal maxPh,
            String severity,
            String label,
            String colorToken,
            int sortOrder) {
    }
}
