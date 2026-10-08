package com.catcheck.scan.application;

import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.ChartMatcher;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.ConfidenceCalculator;
import com.catcheck.scan.domain.color.DeltaE2000;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.RobustStats;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ExternalServiceException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestration toàn bộ pipeline S1-S10 (p6 §6.5.1) cho MỘT ảnh — thuần tính toán, KHÔNG chạm DB,
 * KHÔNG chạm credit. Gọi TRƯỚC khi mở transaction (xem {@code SubmitScanService}).
 *
 * <p><b>Phạm vi MVP (M3):</b> chỉ nhánh hiệu chỉnh {@link CalibrationMethod#SUBSTRATE_WB} /
 * {@link CalibrationMethod#NONE} — không có {@link CalibrationMethod#CARD_CCM} (cần toạ độ patch
 * của {@code reference_card_layout} mà {@link ChartCatalog} chưa công bố, xem
 * {@code docs/handovers/A6.md}). {@link VisionEngine#detectCard} vẫn được gọi thật (OpenCV) để
 * lấy tín hiệu chất lượng {@code CARD_NOT_FOUND}, không dùng cho CCM.</p>
 */
@Service
public class ColorPipelineService {

    private final RawImagePort rawImagePort;
    private final VisionEngine visionEngine;
    private final ChartCatalog chartCatalog;
    private final Clock clock;

    public ColorPipelineService(RawImagePort rawImagePort, VisionEngine visionEngine,
                                 ChartCatalog chartCatalog, Clock clock) {
        this.rawImagePort = rawImagePort;
        this.visionEngine = visionEngine;
        this.chartCatalog = chartCatalog;
        this.clock = clock;
    }

    public PipelineOutcome analyze(byte[] imageBytes, RawImagePort.RoiRect roi, VisionEngine.QuadHint cardQuadHint) {
        long startedAtMillis = clock.millis();

        if (!rawImagePort.isAvailable()) {
            throw new ExternalServiceException(ScanErrorCode.VISION_ENGINE_UNAVAILABLE, "opencv-native");
        }

        PhChart chart = chartCatalog.findActiveChart(ScanThresholds.DEFAULT_PRODUCT_LINE, null)
                .orElseThrow(() -> new BusinessRuleException(ScanErrorCode.SCAN_CHART_UNAVAILABLE));
        List<PhBandClassifier.Band> bands = chartCatalog.findGlobalBands();

        RawImagePort.DecodedImage decoded = rawImagePort.decode(imageBytes);
        int longEdge = Math.max(decoded.width(), decoded.height());
        if (longEdge < ScanThresholds.MIN_EDGE_PX) {
            throw new BusinessRuleException(ScanErrorCode.IMAGE_TOO_SMALL, longEdge, ScanThresholds.MIN_EDGE_PX);
        }
        if (longEdge > ScanThresholds.MAX_EDGE_PX) {
            throw new BusinessRuleException(ScanErrorCode.IMAGE_TOO_LARGE, longEdge, ScanThresholds.MAX_EDGE_PX);
        }

        VisionEngine.FrameHandle roiFrame = rawImagePort.cropToRoi(decoded.frame(), roi);
        VisionEngine.CardDetectionResult cardResult = visionEngine.detectCard(decoded.frame(), cardQuadHint);
        RawImagePort.QualityMetrics quality = rawImagePort.measureQuality(roiFrame);

        List<QualityFlag> flags = new ArrayList<>();
        boolean blocking = false;
        if (quality.blurVariance() < ScanThresholds.BLUR_VAR_MIN) {
            flags.add(new QualityFlag(QualityFlagCode.BLURRY, QualityFlagSeverity.BLOCKING));
            blocking = true;
        }
        if (quality.meanLuma() < ScanThresholds.MEAN_LUMA_MIN) {
            flags.add(new QualityFlag(QualityFlagCode.TOO_DARK, QualityFlagSeverity.BLOCKING));
            blocking = true;
        } else if (quality.meanLuma() > ScanThresholds.MEAN_LUMA_MAX) {
            flags.add(new QualityFlag(QualityFlagCode.TOO_BRIGHT, QualityFlagSeverity.BLOCKING));
            blocking = true;
        }
        if (quality.clipHigh() > ScanThresholds.CLIP_HIGH_MAX) {
            flags.add(new QualityFlag(QualityFlagCode.OVEREXPOSED, QualityFlagSeverity.BLOCKING));
            blocking = true;
        }
        if (quality.clipLow() > ScanThresholds.CLIP_LOW_MAX) {
            flags.add(new QualityFlag(QualityFlagCode.UNDEREXPOSED_PATCH, QualityFlagSeverity.WARN));
        }
        if (quality.lumaGradient() > ScanThresholds.LUMA_GRADIENT_MAX) {
            flags.add(new QualityFlag(QualityFlagCode.UNEVEN_LIGHTING, QualityFlagSeverity.WARN));
        }
        if (!cardResult.found()) {
            flags.add(new QualityFlag(QualityFlagCode.CARD_NOT_FOUND, QualityFlagSeverity.WARN));
        }

        Map<String, Object> qualityMetricsJson = Map.of(
                "blurVar", quality.blurVariance(),
                "meanLuma", quality.meanLuma(),
                "clipHigh", quality.clipHigh(),
                "clipLow", quality.clipLow(),
                "lumaGradient", quality.lumaGradient());

        if (blocking) {
            return inconclusiveOutcome(flags, qualityMetricsJson, chart, startedAtMillis, decoded);
        }

        RawImagePort.CalibrationResult calibration = rawImagePort.calibrate(roiFrame);
        if (calibration.method() == CalibrationMethod.NONE) {
            flags.add(new QualityFlag(QualityFlagCode.CALIBRATION_UNAVAILABLE, QualityFlagSeverity.WARN));
        }

        List<VisionEngine.Blob> blobs = visionEngine.selectIndicatorGrains(
                calibration.xyzFrame(), calibration.substrateLab(), VisionEngine.GrainThresholds.standard());
        // Kích thước ảnh PHÂN TÍCH (có thể đã thu nhỏ): blob.pixelCount() tính trên cùng hệ toạ độ này.
        VisionEngine.FrameSize roiSize = calibration.xyzFrame().size();
        int roiPixelCount = Math.max(1, roiSize.width() * roiSize.height());
        int totalBlobPixels = blobs.stream().mapToInt(VisionEngine.Blob::pixelCount).sum();
        double indicatorRatio = Math.min(1.0, (double) totalBlobPixels / roiPixelCount);

        if (blobs.isEmpty() || indicatorRatio < ScanThresholds.HARD_MIN_COVERAGE) {
            flags.add(new QualityFlag(QualityFlagCode.NO_INDICATOR_GRAINS, QualityFlagSeverity.BLOCKING));
            return inconclusiveOutcome(flags, qualityMetricsJson, chart, startedAtMillis, decoded);
        }
        if (indicatorRatio < ScanThresholds.MIN_COVERAGE_STANDARD) {
            flags.add(new QualityFlag(QualityFlagCode.LOW_GRANULE_COVERAGE, QualityFlagSeverity.WARN));
        }

        List<Lab> grainLabs = blobs.stream().map(VisionEngine.Blob::labMean).toList();
        RobustStats.Result stats = RobustStats.analyze(grainLabs);
        if (stats.fewBlobs()) {
            flags.add(new QualityFlag(QualityFlagCode.FEW_BLOBS, QualityFlagSeverity.WARN));
        }
        if (stats.looksMixed()) {
            flags.add(new QualityFlag(QualityFlagCode.MIXED_COLORS, QualityFlagSeverity.WARN));
        }
        Lab sampleLab = stats.median();

        ChartMatcher.Match match = ChartMatcher.match(sampleLab, chart, null);
        if (match.outOfChartRange()) {
            flags.add(new QualityFlag(QualityFlagCode.OUT_OF_CHART_RANGE, QualityFlagSeverity.WARN));
        }
        Lab projected = Lab.midpoint(match.lowerPoint().lab(), match.upperPoint().lab(), match.interpolatedT());
        double perpResidual = DeltaE2000.deltaE(sampleLab, projected, chart.deltaEParams());

        ConfidenceCalculator.CalibrationTier tier = switch (calibration.method()) {
            case CARD_CCM -> ConfidenceCalculator.CalibrationTier.A;
            case SUBSTRATE_WB -> ConfidenceCalculator.CalibrationTier.B;
            case NONE -> ConfidenceCalculator.CalibrationTier.C;
        };
        ConfidenceCalculator.Inputs inputs = new ConfidenceCalculator.Inputs(
                calibration.calibrationResidualDe00Proxy(),
                match.deltaEMin(),
                stats.spreadDeltaE00(),
                indicatorRatio,
                quality.blurVariance(),
                ScanThresholds.BLUR_VAR_MIN,
                quality.clipHigh(),
                quality.clipLow(),
                quality.lumaGradient(),
                stats.blobCount());
        ConfidenceCalculator.Result confidenceResult = ConfidenceCalculator.calculate(
                inputs, match.phEstimate(), chart, match.deltaEMin(), stats.spreadDeltaE00(),
                stats.blobCount(), perpResidual, tier, ConfidenceCalculator.Thresholds.standard());

        PhBandClassifier.Result classified = PhBandClassifier.classify(
                match.phEstimate(), confidenceResult.phLow(), confidenceResult.phHigh(),
                confidenceResult.confidence(), bands, false);

        boolean inconclusive = classified.code() == PhBandClassifier.Code.INCONCLUSIVE;
        int processingMs = (int) (clock.millis() - startedAtMillis);

        UUID matchedPointId = parseUuidOrNull(match.matchedPointId());
        int matchedSegmentK = chart.points().indexOf(match.lowerPoint());

        Map<String, Object> calibrationJson = Map.of(
                "method", calibration.method().name(),
                "wbGains", calibration.wbGains(),
                "neutralPixelRatio", calibration.neutralPixelRatio());

        return new PipelineOutcome(
                inconclusive,
                ScanClassification.valueOf(classified.code().name()),
                inconclusive ? null : decimal1(match.phEstimate()),
                inconclusive ? null : decimal1(confidenceResult.phLow()),
                inconclusive ? null : decimal1(confidenceResult.phHigh()),
                decimal3(confidenceResult.confidence()),
                confidenceResult.band().name(),
                classified.nearBoundary(),
                decimal3(sampleLab.l()),
                decimal3(sampleLab.a()),
                decimal3(sampleLab.b()),
                decimal2(stats.spreadDeltaE00()),
                stats.blobCount(),
                decimal4(indicatorRatio),
                decimal3(calibration.substrateLab().l()),
                decimal3(calibration.substrateLab().a()),
                decimal3(calibration.substrateLab().b()),
                decimal3(match.deltaEMin()),
                decimal3(perpResidual),
                (int) Math.round(match.matchPercent()),
                matchedPointId,
                matchedSegmentK < 0 ? null : matchedSegmentK,
                decimal4(match.interpolatedT()),
                calibration.method(),
                decimal3(calibration.calibrationResidualDe00Proxy()),
                calibrationJson,
                qualityMetricsJson,
                List.copyOf(flags),
                parseUuidOrNull(chart.id()),
                chart.version(),
                chart.isPlaceholder(),
                PipelineOutcome.ENGINE_VERSION,
                processingMs,
                inconclusive ? null : ColorSpace.labToHex(sampleLab),
                decoded.width(), decoded.height(), decoded.contentType());
    }

    private PipelineOutcome inconclusiveOutcome(List<QualityFlag> flags, Map<String, Object> qualityMetricsJson,
                                                 PhChart chart, long startedAtMillis,
                                                 RawImagePort.DecodedImage decoded) {
        int processingMs = (int) (clock.millis() - startedAtMillis);
        // Confidence thấp cố định khi quality gate chặn TRƯỚC khi S8/S9 kịp chạy thật.
        BigDecimal lowConfidence = new BigDecimal("0.20");
        return new PipelineOutcome(
                true, ScanClassification.INCONCLUSIVE, null, null, null, lowConfidence, "LOW", false,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, CalibrationMethod.NONE, null, Map.of(), qualityMetricsJson,
                List.copyOf(flags), parseUuidOrNull(chart.id()), chart.version(), chart.isPlaceholder(),
                PipelineOutcome.ENGINE_VERSION, processingMs, null,
                decoded.width(), decoded.height(), decoded.contentType());
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private BigDecimal decimal1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal3(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal4(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }
}
