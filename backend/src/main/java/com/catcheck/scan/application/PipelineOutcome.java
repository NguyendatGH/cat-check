package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.ScanClassification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
