package com.catcheck.scan.application;

import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanAnalysis;

import java.util.List;
import java.util.Map;

/**
 * Dựng lại {@link PipelineOutcome} từ một {@link ScanAnalysis} đã lưu — dùng khi replay
 * idempotent (p5 R8) hoặc khi trả {@code GET /scans/{id}/analysis}, để không phải chạy lại
 * pipeline chỉ để có cùng hình dạng response.
 */
final class PipelineOutcomeMapper {

    private PipelineOutcomeMapper() {
    }

    static PipelineOutcome fromEntity(ScanAnalysis a) {
        boolean inconclusive = a.getClassification() != null
                && "INCONCLUSIVE".equals(a.getClassification().name());
        return new PipelineOutcome(
                inconclusive,
                a.getClassification(),
                a.getPhValue(),
                a.getPhLow(),
                a.getPhHigh(),
                a.getConfidence(),
                null,
                a.isNearBoundary(),
                a.getLabL(),
                a.getLabA(),
                a.getLabB(),
                a.getLabSpreadDe00(),
                a.getBlobCount(),
                a.getIndicatorPixelRatio(),
                a.getSubstrateLabL(),
                a.getSubstrateLabA(),
                a.getSubstrateLabB(),
                a.getDeltaEMin(),
                a.getPerpResidualDe00(),
                a.getMatchPercent(),
                a.getMatchedPointId(),
                a.getMatchedSegmentK(),
                a.getMatchedT(),
                a.getCalibrationMethod(),
                a.getCalibrationResidualDe00(),
                a.getCalibration(),
                a.getQualityMetrics(),
                fromFlagMaps(a.getQualityFlags()),
                a.getChartId(),
                a.getChartVersion(),
                false,
                a.getEngineVersion(),
                a.getProcessingMs() == null ? 0 : a.getProcessingMs(),
                null,
                0, 0, null);
    }

    private static List<QualityFlag> fromFlagMaps(List<Map<String, Object>> maps) {
        if (maps == null) {
            return List.of();
        }
        return maps.stream()
                .map(m -> new QualityFlag(
                        QualityFlagCode.valueOf(String.valueOf(m.get("code"))),
                        QualityFlagSeverity.valueOf(String.valueOf(m.get("severity")))))
                .toList();
    }
}
