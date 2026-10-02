package com.catcheck.cat.api.dto;

import com.catcheck.cat.application.CatInsightService;
import com.catcheck.cat.application.spi.ScanInsightPort;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * D12 {@code GET /cats/{catId}/summary} — hop dong o p8 §8.9 dong 1185:
 * {@code {catId, lastScan: {...}|null, scanCount30d, inRangeRatio30d, unacknowledgedFlagCount,
 * nextReminderAt, hasEnoughDataForTrend}}.
 */
public record CatSummaryResponse(
        UUID catId,
        LastScan lastScan,
        long scanCount30d,
        BigDecimal inRangeRatio30d,
        long unacknowledgedFlagCount,
        Instant nextReminderAt,
        boolean hasEnoughDataForTrend) {

    public record LastScan(
            UUID scanId,
            Instant capturedAt,
            BigDecimal phValue,
            String classification,
            BigDecimal confidence) {

        static LastScan from(ScanInsightPort.LastScan source) {
            return new LastScan(
                    source.scanId(), source.capturedAt(), source.phValue(),
                    source.classification(), source.confidence());
        }
    }

    public static CatSummaryResponse from(CatInsightService.CatSummaryView view) {
        return new CatSummaryResponse(
                view.catId(),
                view.lastScan() == null ? null : LastScan.from(view.lastScan()),
                view.scanCount30d(),
                view.inRangeRatio30d(),
                view.unacknowledgedFlagCount(),
                view.nextReminderAt(),
                view.hasEnoughDataForTrend());
    }
}
