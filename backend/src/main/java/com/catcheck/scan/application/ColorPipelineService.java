package com.catcheck.scan.application;

import com.catcheck.scan.api.ScanErrorCode;
import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.CardLayoutCatalog;
import com.catcheck.scan.domain.color.CcmSolver;
import com.catcheck.scan.domain.color.ChartMatcher;
import com.catcheck.scan.domain.color.ColorSamples;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.ConfidenceCalculator;
import com.catcheck.scan.domain.color.DeltaE2000;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.RgbLinear;
import com.catcheck.scan.domain.color.RobustStats;
import com.catcheck.scan.domain.color.SubstrateProfileCatalog;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.shared.error.BusinessRuleException;
import com.catcheck.shared.error.ExternalServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Orchestration toàn bộ pipeline S1-S10 (p6 §6.5.1) cho MỘT ảnh — thuần tính toán, KHÔNG chạm DB,
 * KHÔNG chạm credit. Gọi TRƯỚC khi mở transaction (xem {@code SubmitScanService}).
 *
 * <p><b>Hiệu chỉnh màu (S4):</b> cả ba nhánh {@link CalibrationMethod} đều chạy thật.
 * {@link VisionEngine#detectCard} dò thẻ tham chiếu (ArUco → QR → contour) và nắn phẳng về khung
 * chuẩn 600×380; khi thẻ dùng được VÀ {@link CardLayoutCatalog} có layout active (bảng
 * {@code reference_card_layout}), pipeline lấy mẫu các patch {@code role=NEUTRAL} trên thẻ, fit
 * {@link LinearizationCurve} rồi giải {@link CcmSolver} so với {@code ref_lab} của chính patch đó
 * — ra {@link CalibrationMethod#CARD_CCM} (tier A). Hậu kiểm bằng ΔE00 trên đúng các patch NEUTRAL
 * sau khi áp ma trận: residual vượt {@link ScanThresholds#CARD_CCM_MAX_RESIDUAL_DE00} (hoặc không
 * có thẻ/không có layout active/hệ CCM suy thoái) thì rơi êm về {@link CalibrationMethod#SUBSTRATE_WB}
 * kèm cờ {@link QualityFlagCode#CARD_UNUSABLE} — đúng nguyên tắc suy giảm p6 §6.5.5: CARD_CCM là
 * lựa chọn TỐT HƠN, không phải lựa chọn DUY NHẤT. Nhánh {@link CalibrationMethod#SUBSTRATE_WB} /
 * {@link CalibrationMethod#NONE} khi không có thẻ không đổi so với M3.</p>
 */
@Service
public class ColorPipelineService {

    private static final Logger log = LoggerFactory.getLogger(ColorPipelineService.class);

    private final RawImagePort rawImagePort;
    private final VisionEngine visionEngine;
    private final ChartCatalog chartCatalog;
    private final CardLayoutCatalog cardLayoutCatalog;
    private final SubstrateProfileCatalog substrateProfileCatalog;
    private final Clock clock;

    public ColorPipelineService(RawImagePort rawImagePort, VisionEngine visionEngine,
                                 ChartCatalog chartCatalog, CardLayoutCatalog cardLayoutCatalog,
                                 SubstrateProfileCatalog substrateProfileCatalog, Clock clock) {
        this.rawImagePort = rawImagePort;
        this.visionEngine = visionEngine;
        this.chartCatalog = chartCatalog;
        this.cardLayoutCatalog = cardLayoutCatalog;
        this.substrateProfileCatalog = substrateProfileCatalog;
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

        Lab referenceSubstrateLab = substrateProfileCatalog
                .findReferenceLab(ScanThresholds.DEFAULT_PRODUCT_LINE, null)
                .orElse(null);
        RawImagePort.CalibrationResult calibration = rawImagePort.calibrate(roiFrame, referenceSubstrateLab);
        if (cardResult.isUsable()) {
            calibration = tryCardCcm(cardResult.canonicalCard(), roiFrame, calibration, flags);
        }
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
        log.info("scan.indicator-recognition blobs={} coverage={} substrateLab={} indicatorLab={} "
                        + "chartMatchPh={} deltaE={} outOfChartRange={}",
                stats.blobCount(), indicatorRatio, calibration.substrateLab(), sampleLab,
                match.phEstimate(), match.deltaEMin(), match.outOfChartRange());
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

    /**
     * S4 (nhánh CARD_CCM) — thử giải CCM từ các patch {@code role=NEUTRAL} trên ảnh thẻ đã nắn
     * phẳng. CARD_CCM là lựa chọn <b>tốt hơn</b> {@code fallback} (SUBSTRATE_WB/NONE đã tính sẵn),
     * không phải lựa chọn duy nhất (p6 §6.5.5): bất kỳ lý do nào khiến không giải/không tin được
     * CCM (chưa publish layout, không đủ patch đọc được, hệ suy thoái, ma trận suy biến, residual
     * quá lớn) đều rơi êm về {@code fallback} kèm cờ {@link QualityFlagCode#CARD_UNUSABLE}.
     *
     * <h2>Vì sao phải kiểm định thức, không chỉ residual</h2>
     * <p>Mọi patch {@code role=NEUTRAL} có {@code ref_lab} với {@code a=b=0} — nghĩa là vế phải
     * ({@code reference}) của ba hệ WLS trong {@link CcmSolver#solve} (một hệ mỗi kênh đầu ra)
     * <b>giống hệt nhau</b>, nên ba hàng của ma trận giải được cũng giống hệt nhau một cách TẤT
     * YẾU về đại số — bất kể dữ liệu đo được "sạch" cỡ nào. Một ma trận ba hàng giống nhau chiếu
     * MỌI màu về đúng một trục xám (det = 0, hạng 1): residual đo trên chính 5 patch train vẫn có
     * thể rất nhỏ (nó được fit để đúng y hệt 5 điểm đó), nhưng áp lên ROI thật sẽ xoá sạch chroma
     * của mọi pixel khác — hỏng cả S6 (tách hạt cần chroma) lẫn S8 (khớp bảng màu cần hue/chroma).
     * Đây là đúng dạng lỗi {@link Matrix3#inverse()} đã cảnh báo ("hỏng âm thầm đắt nhất"), nên áp
     * cùng nguyên tắc: từ chối ma trận gần suy biến thay vì tin một residual chỉ đo trên tập train.
     */
    private RawImagePort.CalibrationResult tryCardCcm(VisionEngine.FrameHandle canonicalCard,
                                                        VisionEngine.FrameHandle roiFrame,
                                                        RawImagePort.CalibrationResult fallback,
                                                        List<QualityFlag> flags) {
        Optional<CardLayout> layout = cardLayoutCatalog.findActiveLayout(ScanThresholds.DEFAULT_CARD_LAYOUT_CODE);
        if (layout.isEmpty()) {
            return fallback;
        }

        List<RawImagePort.CardPatchSample> patchSamples =
                rawImagePort.sampleNeutralPatches(canonicalCard, layout.get());
        if (patchSamples.size() < LinearizationCurve.MIN_GRAY_PATCHES) {
            flags.add(new QualityFlag(QualityFlagCode.CARD_UNUSABLE, QualityFlagSeverity.WARN));
            return fallback;
        }

        // Fit linearization: dải xám NEUTRAL cho cả "encoded" (8-bit đo được) và "linear" tham
        // chiếu đúng (suy từ ref_lab — trung tính nên r=g=b trong linear, lấy trung bình 3 kênh
        // cho chắc trước sai số số học).
        double[] encoded = new double[patchSamples.size()];
        double[] linearReferences = new double[patchSamples.size()];
        for (int i = 0; i < patchSamples.size(); i++) {
            RawImagePort.CardPatchSample sample = patchSamples.get(i);
            encoded[i] = ((sample.encodedR() + sample.encodedG() + sample.encodedB()) / 3.0) / 255.0;
            RgbLinear refLinear = ColorSpace.labToLinearRgb(sample.referenceLab());
            linearReferences[i] = (refLinear.r() + refLinear.g() + refLinear.b()) / 3.0;
        }
        LinearizationCurve curve = LinearizationCurve.fitOrStandard(encoded, linearReferences);

        List<ColorSamples.Sample> ccmSamples = new ArrayList<>(patchSamples.size());
        for (RawImagePort.CardPatchSample sample : patchSamples) {
            RgbLinear measured = new RgbLinear(
                    curve.linearize(sample.encodedR() / 255.0),
                    curve.linearize(sample.encodedG() / 255.0),
                    curve.linearize(sample.encodedB() / 255.0));
            ccmSamples.add(ColorSamples.Sample.of(measured, ColorSpace.labToLinearRgb(sample.referenceLab())));
        }

        Matrix3 ccm;
        try {
            ccm = CcmSolver.solve(ccmSamples);
        } catch (IllegalArgumentException ex) {
            flags.add(new QualityFlag(QualityFlagCode.CARD_UNUSABLE, QualityFlagSeverity.WARN));
            return fallback;
        }

        if (Math.abs(ccm.determinant()) < ScanThresholds.CARD_CCM_MIN_ABS_DETERMINANT) {
            flags.add(new QualityFlag(QualityFlagCode.CARD_UNUSABLE, QualityFlagSeverity.WARN));
            return fallback;
        }

        double residualDe00 = residualDeltaE(ccm, ccmSamples, patchSamples);
        if (residualDe00 > ScanThresholds.CARD_CCM_MAX_RESIDUAL_DE00) {
            flags.add(new QualityFlag(QualityFlagCode.CARD_UNUSABLE, QualityFlagSeverity.WARN));
            return fallback;
        }

        return rawImagePort.applyCardCcm(roiFrame, curve, ccm, residualDe00);
    }

    /** ΔE00 trung bình của các patch NEUTRAL sau khi áp {@code ccm}, so với {@code ref_lab} gốc. */
    private double residualDeltaE(Matrix3 ccm, List<ColorSamples.Sample> ccmSamples,
                                   List<RawImagePort.CardPatchSample> patchSamples) {
        double sum = 0.0;
        for (int i = 0; i < ccmSamples.size(); i++) {
            Lab correctedLab = ColorSpace.linearRgbToLab(ccm.apply(ccmSamples.get(i).measured()));
            sum += DeltaE2000.deltaE(correctedLab, patchSamples.get(i).referenceLab(), DeltaE2000Params.CAT_CHECK);
        }
        return sum / ccmSamples.size();
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
