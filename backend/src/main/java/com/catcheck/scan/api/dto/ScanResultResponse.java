package com.catcheck.scan.api.dto;

import com.catcheck.scan.application.PipelineOutcome;
import com.catcheck.scan.application.ScanSubmitResult;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.port.ScanQueryRepository.Row;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response {@code POST /api/v1/scans} và {@code GET /scans/{id}} (p8 §8.5.4).
 *
 * <p>{@code scanId} là {@code string | null} — {@code null} khi và chỉ khi {@code INCONCLUSIVE}
 * (không có bản ghi hiển thị được).</p>
 */
public record ScanResultResponse(
        String scanId,
        String scanRequestId,
        String catId,
        String catName,
        String assignment,
        Instant capturedAt,
        String status,

        BigDecimal phValue,
        BigDecimal phLow,
        BigDecimal phHigh,
        String classification,
        String bandCode,
        String labelKey,
        boolean nearBoundary,
        BigDecimal confidence,
        String confidenceBand,

        Integer matchPercent,
        String displayHex,
        String calibrationMethod,
        String captureSource,
        String chartCode,
        Integer chartVersion,
        boolean chartIsPlaceholder,
        String engineVersion,

        List<QualityFlagResponse> qualityFlags,

        boolean creditCharged,
        Integer creditBalanceAfter,
        boolean isTrial,
        boolean imageStored,
        String imageUrl,
        Instant imageRetainedUntil,
        String storeImageReason,
        Instant reassignableUntil,
        int reassignRemaining,

        List<TriggeredFlagResponse> triggeredFlags,
        String disclaimerKey,
        String emergencyDisclaimerKey,
        Integer processingMs,

        String retryHintKey,
        Instant disputedAt,
        String disputedNote
) {

    public static final String DISCLAIMER_SHORT_KEY = "legal.disclaimer.short";
    public static final String DISCLAIMER_EMERGENCY_KEY = "legal.disclaimer.emergency";

    public static ScanResultResponse from(ScanSubmitResult result, String imageUrl) {
        PipelineOutcome o = result.outcome();
        boolean inconclusive = o.inconclusive();
        return new ScanResultResponse(
                result.scanId() == null ? null : result.scanId().toString(),
                result.scanRequestId(),
                result.catId() == null ? null : result.catId().toString(),
                result.catName(),
                result.assignment(),
                result.capturedAt(),
                result.status(),
                o.phValue(),
                o.phLow(),
                o.phHigh(),
                o.classification().name(),
                o.classification().name(),
                "phBand." + o.classification().name() + ".label",
                o.nearBoundary(),
                o.confidence(),
                o.confidenceBand(),
                o.matchPercent(),
                o.displayHex(),
                o.calibrationMethod() == null ? null : o.calibrationMethod().name(),
                null,
                o.chartId() == null ? null : o.chartId().toString(),
                o.chartVersion(),
                o.chartIsPlaceholder(),
                o.engineVersion(),
                mapFlags(o.qualityFlags()),
                result.creditCharged(),
                result.creditBalanceAfter(),
                result.isTrial(),
                result.imageStored(),
                result.imageStored() ? imageUrl : null,
                result.imageRetainedUntil(),
                result.storeImageReason(),
                result.reassignableUntil(),
                result.reassignRemaining(),
                List.of(),
                DISCLAIMER_SHORT_KEY,
                DISCLAIMER_EMERGENCY_KEY,
                o.processingMs(),
                inconclusive ? retryHintFor(o) : null,
                null, null);
    }

    private static String retryHintFor(PipelineOutcome o) {
        boolean blurry = o.qualityFlags().stream().anyMatch(f -> f.code().name().equals("BLURRY"));
        return blurry ? "scan.retry.blurry" : "scan.retry.generic";
    }

    private static List<QualityFlagResponse> mapFlags(List<QualityFlag> flags) {
        return flags.stream()
                .map(f -> new QualityFlagResponse(f.code().name(), f.severity().name(), f.messageKey()))
                .toList();
    }

    /** Dùng cho {@code GET /scans/{id}} và {@code GET /scans/by-request/{id}} — nguồn là read-model, không phải pipeline mới. */
    public static ScanResultResponse from(Row row, String catName, String imageUrl) {
        return new ScanResultResponse(
                row.scanId().toString(),
                row.scanRequestId(),
                row.catId() == null ? null : row.catId().toString(),
                catName,
                row.assignment(),
                row.capturedAt(),
                row.status(),
                row.phValue(),
                row.phLow(),
                row.phHigh(),
                row.classification(),
                row.classification(),
                row.classification() == null ? null : "phBand." + row.classification() + ".label",
                row.nearBoundary(),
                row.confidence(),
                null,
                row.matchPercent(),
                null,
                row.calibrationMethod(),
                null,
                row.chartId() == null ? null : row.chartId().toString(),
                row.chartVersion(),
                row.chartIsPlaceholder(),
                row.engineVersion(),
                mapRowFlags(row.qualityFlags()),
                row.creditCharged(),
                null,
                row.trial(),
                row.imageStored(),
                row.imageStored() ? imageUrl : null,
                row.imageExpiresAt(),
                row.storeImageReason(),
                row.capturedAt().plusSeconds(ScanThresholds.REASSIGN_WINDOW_HOURS * 3600L),
                ScanThresholds.REASSIGN_MAX_COUNT - row.reassignCount(),
                List.of(),
                DISCLAIMER_SHORT_KEY,
                DISCLAIMER_EMERGENCY_KEY,
                row.processingMs(),
                null,
                row.disputedAt(),
                row.disputedNote());
    }

    @SuppressWarnings("unchecked")
    private static List<QualityFlagResponse> mapRowFlags(List<java.util.Map<String, Object>> flags) {
        if (flags == null) {
            return List.of();
        }
        return flags.stream()
                .map(m -> {
                    String code = String.valueOf(m.get("code"));
                    String severity = String.valueOf(m.get("severity"));
                    return new QualityFlagResponse(code, severity, "scan.quality." + code);
                })
                .toList();
    }
}
