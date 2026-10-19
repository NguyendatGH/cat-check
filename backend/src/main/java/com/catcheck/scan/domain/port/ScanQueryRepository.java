package com.catcheck.scan.domain.port;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ScanQueryRepository {

    Page findHistory(HistoryFilter filter, String cursor, int limit);

    Summary summarize(HistoryFilter filter);

    List<RuleRow> findRecentForRules(UUID catId, Instant since, int limit);

    java.util.Optional<Row> findRowByScanId(UUID scanId);

    OffsetPage findByUserForAdmin(UUID userId, int offset, int limit);

    List<Row> findForExport(UUID catId, Instant from, Instant to);

    record HistoryFilter(
            UUID userId,
            UUID catId,
            String assignment,
            List<String> classifications,
            Instant from,
            Instant to,
            Boolean disputed
    ) {
    }

    record Page(List<Row> items, String nextCursor) {
    }

    record OffsetPage(List<Row> items, long totalElements) {

        public OffsetPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record Row(
            UUID scanId,
            UUID catId,
            UUID userId,
            String assignment,
            Instant capturedAt,
            String status,
            BigDecimal phValue,
            BigDecimal phLow,
            BigDecimal phHigh,
            String classification,
            UUID bandId,
            BigDecimal confidence,
            boolean nearBoundary,
            BigDecimal labL,
            BigDecimal labA,
            BigDecimal labB,
            Integer matchPercent,
            String calibrationMethod,
            UUID chartId,
            Integer chartVersion,
            boolean chartIsPlaceholder,
            String engineVersion,
            List<Map<String, Object>> qualityFlags,
            boolean creditCharged,
            boolean trial,
            boolean imageStored,
            Instant imageExpiresAt,
            String storeImageReason,
            Instant disputedAt,
            String disputedNote,
            short reassignCount,
            Integer processingMs,
            String scanRequestId
    ) {
    }

    record RuleRow(
            UUID scanId,
            Instant capturedAt,
            String classification,
            BigDecimal phValue,
            BigDecimal confidence,
            boolean nearBoundary,
            String calibrationMethod
    ) {
    }

    record Summary(
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
    }
}
