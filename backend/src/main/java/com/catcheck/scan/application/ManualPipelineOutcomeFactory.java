package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.ScanThresholds;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.ConfidenceCalculator;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ManualPipelineOutcomeFactory {

    public static final String ENGINE_VERSION = "manual-1.0.0";

    public static final BigDecimal DEFAULT_CONFIDENCE = new BigDecimal("0.800");

    public static final BigDecimal INCONCLUSIVE_CONFIDENCE = new BigDecimal("0.200");

    private static final BigDecimal CI_HALF_WIDTH = new BigDecimal("0.1");

    private final ChartCatalog chartCatalog;

    public ManualPipelineOutcomeFactory(ChartCatalog chartCatalog) {
        this.chartCatalog = chartCatalog;
    }

    public PipelineOutcome fromManualPh(BigDecimal phValue,
                                        ScanClassification forcedClassification,
                                        BigDecimal requestedConfidence,
                                        List<QualityFlag> qualityFlags) {

        List<QualityFlag> flags = qualityFlags == null ? List.of() : List.copyOf(qualityFlags);
        BigDecimal confidence = (requestedConfidence == null ? DEFAULT_CONFIDENCE : requestedConfidence)
                .setScale(3, RoundingMode.HALF_UP);
        boolean blocking = flags.stream().anyMatch(f -> f.severity() == QualityFlagSeverity.BLOCKING);

        Optional<PhChart> chart = chartCatalog.findActiveChart(ScanThresholds.DEFAULT_PRODUCT_LINE, null);

        BigDecimal ph = phValue == null ? null : phValue.setScale(1, RoundingMode.HALF_UP);
        BigDecimal phLow = ph == null ? null : ph.subtract(CI_HALF_WIDTH);
        BigDecimal phHigh = ph == null ? null : ph.add(CI_HALF_WIDTH);

        ScanClassification classification;
        boolean nearBoundary;
        if (ph == null) {
            classification = ScanClassification.INCONCLUSIVE;
            nearBoundary = false;
        } else {
            PhBandClassifier.Result classified = PhBandClassifier.classify(
                    ph.doubleValue(), phLow.doubleValue(), phHigh.doubleValue(),
                    confidence.doubleValue(), chartCatalog.findGlobalBands(), blocking);
            classification = ScanClassification.valueOf(classified.code().name());
            nearBoundary = classified.nearBoundary();
        }
        if (forcedClassification != null) {
            classification = forcedClassification;
        }

        boolean inconclusive = classification == ScanClassification.INCONCLUSIVE;
        if (inconclusive) {
            ph = null;
            phLow = null;
            phHigh = null;
            nearBoundary = false;
            if (requestedConfidence == null) {
                confidence = INCONCLUSIVE_CONFIDENCE;
            }
        }

        return new PipelineOutcome(
                inconclusive,
                classification,
                ph, phLow, phHigh,
                confidence,
                confidenceBandOf(confidence),
                nearBoundary,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                CalibrationMethod.NONE,
                null,
                Map.of("method", CalibrationMethod.NONE.name(), "source", "MANUAL"),
                Map.of(),
                flags,
                chart.map(c -> parseUuidOrNull(c.id())).orElse(null),
                chart.map(PhChart::version).orElse(null),
                chart.map(PhChart::isPlaceholder).orElse(false),
                ENGINE_VERSION,
                0,
                null,
                0, 0, null);
    }

    public static QualityFlag flagOf(QualityFlagCode code) {
        QualityFlagSeverity severity = switch (code) {
            case BLURRY, TOO_DARK, TOO_BRIGHT, OVEREXPOSED, NO_INDICATOR_GRAINS -> QualityFlagSeverity.BLOCKING;
            case UNDEREXPOSED_PATCH, UNEVEN_LIGHTING, CARD_NOT_FOUND, CARD_UNUSABLE, CALIBRATION_UNAVAILABLE,
                 LOW_GRANULE_COVERAGE, FEW_BLOBS, MIXED_COLORS, OUT_OF_CHART_RANGE -> QualityFlagSeverity.WARN;
        };
        return new QualityFlag(code, severity);
    }

    private String confidenceBandOf(BigDecimal confidence) {
        double value = confidence.doubleValue();
        if (value >= 0.75) {
            return "HIGH";
        }
        return value >= ConfidenceCalculator.MIN_RESULT_CONFIDENCE ? "MEDIUM" : "LOW";
    }

    private UUID parseUuidOrNull(String value) {
        try {
            return value == null ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
