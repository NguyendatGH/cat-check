package com.catcheck.scan.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScanHistoryQuery {

    List<RecentScan> recentValidScans(UUID catId, Instant since, int limit);

    List<ExportScanRow> scansForExport(UUID catId, Instant from, Instant to);

    record ExportScanRow(
            UUID scanId,
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence,
            boolean nearBoundary,
            boolean disputed,
            String calibrationMethod,
            List<String> qualityFlagCodes,
            boolean imageAvailable
    ) {
    }

    record RecentScan(
            UUID scanId,
            Instant capturedAt,
            String classification,
            BigDecimal phValue,
            BigDecimal confidence,
            boolean nearBoundary,
            String calibrationMethod
    ) {
    }
}
