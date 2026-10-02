package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.ScanClassification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kết quả tính toán của {@link ColorPipelineService} — chưa persist, chưa liên quan credit.
 * {@link ScanPersistenceService} ánh xạ record này vào {@code ScanAnalysis} bên trong transaction.
 *
 * @param inconclusive       {@code true} nếu {@link #classification} = {@code INCONCLUSIVE}
 * @param classification     phân loại (p6 §6.7.1)
 * @param phValue             pH ước lượng, {@code null} khi không đủ dữ liệu để chạy S8 (quality gate chặn sớm)
 * @param matchedPointId      mức pH gần nhất trên bảng màu, {@code null} nếu chưa chạy tới S8
 * @param calibrationMethod   phương pháp hiệu chỉnh đã dùng (p6 §6.5.1 S4/S4b)
 * @param calibration         snapshot JSONB {@code {method, wbGains, neutralPixelRatio}}
 * @param qualityMetrics      snapshot JSONB {@code {blurVar, meanLuma, clipHigh, clipLow, lumaGradient}}
 * @param engineVersion       semver pipeline (bump khi thay đổi thuật toán, p4 D3)
 */
public record PipelineOutcome(
        boolean inconclusive,
        ScanClassification classification,
        BigDecimal phValue,
        BigDecimal phLow,
        BigDecimal phHigh,
        BigDecimal confidence,
        String confidenceBand,
        boolean nearBoundary,
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
        Integer matchPercent,
        UUID matchedPointId,
        Integer matchedSegmentK,
        BigDecimal matchedT,
        CalibrationMethod calibrationMethod,
        BigDecimal calibrationResidualDe00,
        Map<String, Object> calibration,
        Map<String, Object> qualityMetrics,
        List<QualityFlag> qualityFlags,
        UUID chartId,
        Integer chartVersion,
        boolean chartIsPlaceholder,
        String engineVersion,
        int processingMs,
        String displayHex,
        int imageWidth,
        int imageHeight,
        String imageContentType
) {

    public static final String ENGINE_VERSION = "1.0.0";
}
