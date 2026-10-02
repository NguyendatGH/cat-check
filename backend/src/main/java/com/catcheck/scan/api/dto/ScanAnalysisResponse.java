package com.catcheck.scan.api.dto;

import com.catcheck.scan.domain.ScanAnalysis;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** {@code GET /api/v1/scans/{id}/analysis} — màn "Phân tích chi tiết" (p8 §8.4.5 E5, p6 §6.8.2). */
public record ScanAnalysisResponse(
        BigDecimal labL,
        BigDecimal labA,
        BigDecimal labB,
        BigDecimal labSpreadDe00,
        Integer blobCount,
        BigDecimal indicatorPixelRatio,
        BigDecimal substrateLabL,
        BigDecimal substrateLabA,
        BigDecimal substrateLabB,
        BigDecimal deltaEMin,
        BigDecimal perpResidualDe00,
        String calibrationMethod,
        BigDecimal calibrationResidualDe00,
        Map<String, Object> qualityMetrics,
        String chartCode,
        Integer chartVersion,
        String engineVersion,
        Instant computedAt,
        Integer processingMs,
        String recomputeOf
) {

    public static ScanAnalysisResponse from(ScanAnalysis a) {
        return new ScanAnalysisResponse(
                a.getLabL(), a.getLabA(), a.getLabB(), a.getLabSpreadDe00(), a.getBlobCount(),
                a.getIndicatorPixelRatio(), a.getSubstrateLabL(), a.getSubstrateLabA(), a.getSubstrateLabB(),
                a.getDeltaEMin(), a.getPerpResidualDe00(),
                a.getCalibrationMethod() == null ? null : a.getCalibrationMethod().name(),
                a.getCalibrationResidualDe00(), a.getQualityMetrics(),
                a.getChartId() == null ? null : a.getChartId().toString(), a.getChartVersion(),
                a.getEngineVersion(), a.getComputedAt(), a.getProcessingMs(),
                a.getRecomputeOf() == null ? null : a.getRecomputeOf().toString());
    }
}
