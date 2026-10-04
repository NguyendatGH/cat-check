package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.DeltaE2000Params;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đường nhập scan thủ công phải cho ra ĐÚNG cùng mã phân loại như pipeline ảnh với cùng một pH —
 * nếu không, dữ liệu demo sẽ dạy sai cả người xem lẫn mọi rule hạ nguồn đọc
 * {@code scan_analysis.classification}.
 *
 * <p>Bảng band trong test chép đúng seed {@code V9__colorchart.sql} (vốn chép từ p6 §6.7.1),
 * kể cả tính đóng/mở của từng mốc — 6.0 thuộc {@code SLIGHTLY_LOW}, 6.3 và 6.6 thuộc
 * {@code IN_RANGE}, 7.0 thuộc {@code SLIGHTLY_HIGH}.
 */
class ManualPipelineOutcomeFactoryTest {

    private static final UUID CHART_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private final ManualPipelineOutcomeFactory factory =
            new ManualPipelineOutcomeFactory(new FixedChartCatalog());

    @ParameterizedTest(name = "pH {0} ⇒ {1}")
    @DisplayName("p6 §6.7.1 — pH nhập tay đi qua đúng PhBandClassifier, không tự đặt mã")
    @CsvSource({
            "5.4, LOW",
            "5.9, LOW",
            "6.0, SLIGHTLY_LOW",
            "6.2, SLIGHTLY_LOW",
            "6.3, IN_RANGE",
            "6.45, IN_RANGE",
            "6.6, IN_RANGE",
            "6.8, SLIGHTLY_HIGH",
            "7.0, SLIGHTLY_HIGH",
            "7.4, HIGH"
    })
    void classifiesByBandTable(String ph, String expected) {
        PipelineOutcome outcome = factory.fromManualPh(new BigDecimal(ph), null, null, null);

        assertThat(outcome.classification()).isEqualTo(ScanClassification.valueOf(expected));
        assertThat(outcome.inconclusive()).isFalse();
    }

    @Test
    @DisplayName("Hình dạng bản ghi nhập tay: NONE + engineVersion riêng + không có số đo màu bịa")
    void manualOutcomeCarriesNoFabricatedColorMetrics() {
        PipelineOutcome outcome = factory.fromManualPh(new BigDecimal("6.45"), null, null, null);

        assertThat(outcome.calibrationMethod()).isEqualTo(CalibrationMethod.NONE);
        assertThat(outcome.engineVersion()).isEqualTo("manual-1.0.0");
        assertThat(outcome.engineVersion()).isNotEqualTo(PipelineOutcome.ENGINE_VERSION);
        assertThat(outcome.qualityFlags()).isEmpty();
        assertThat(outcome.confidence()).isEqualByComparingTo(ManualPipelineOutcomeFactory.DEFAULT_CONFIDENCE);
        assertThat(outcome.confidenceBand()).isEqualTo("HIGH");
        // Không có ảnh ⇒ không được có một số đo màu nào.
        assertThat(outcome.labL()).isNull();
        assertThat(outcome.labA()).isNull();
        assertThat(outcome.labB()).isNull();
        assertThat(outcome.deltaEMin()).isNull();
        assertThat(outcome.blobCount()).isNull();
        assertThat(outcome.matchedPointId()).isNull();
        assertThat(outcome.displayHex()).isNull();
        // ... nhưng bảng màu đang dùng vẫn phải được ghi nhận: R1–R3 tắt khi placeholder
        // (p6 §6.9.1) nên cờ này không được phép sai.
        assertThat(outcome.chartId()).isEqualTo(CHART_ID);
        assertThat(outcome.chartVersion()).isEqualTo(1);
        assertThat(outcome.chartIsPlaceholder()).isTrue();
    }

    @Test
    @DisplayName("p6 §6.7.2 — CI cắt mốc 6.6 thì nearBoundary bật, mốc nằm đúng ranh thì không")
    void nearBoundaryFollowsConfidenceInterval() {
        assertThat(factory.fromManualPh(new BigDecimal("6.5"), null, null, null).nearBoundary())
                .as("6.5 ± 0.1 = [6.4, 6.6] — chạm mốc nhưng không cắt qua")
                .isFalse();
        assertThat(factory.fromManualPh(new BigDecimal("6.65"), null, null, null).nearBoundary())
                .as("6.7 ± 0.1 = [6.6, 6.8] sau khi làm tròn — vẫn không cắt qua")
                .isFalse();
        assertThat(factory.fromManualPh(new BigDecimal("6.55"), null, null, null).nearBoundary())
                .as("6.6 ± 0.1 = [6.5, 6.7] — cắt qua mốc 6.6")
                .isTrue();
    }

    @Test
    @DisplayName("Cờ BLOCKING ⇒ INCONCLUSIVE và không còn pH hiển thị, như nhánh ảnh hỏng")
    void blockingFlagForcesInconclusive() {
        PipelineOutcome outcome = factory.fromManualPh(
                new BigDecimal("6.45"), null, null,
                List.of(ManualPipelineOutcomeFactory.flagOf(QualityFlagCode.BLURRY)));

        assertThat(outcome.inconclusive()).isTrue();
        assertThat(outcome.classification()).isEqualTo(ScanClassification.INCONCLUSIVE);
        assertThat(outcome.phValue()).isNull();
        assertThat(outcome.phLow()).isNull();
        assertThat(outcome.phHigh()).isNull();
        assertThat(outcome.nearBoundary()).isFalse();
        assertThat(outcome.confidence())
                .as("0,80 tren mot hang 'chua du du lieu de ket luan' la mau thuan tu than")
                .isEqualByComparingTo(ManualPipelineOutcomeFactory.INCONCLUSIVE_CONFIDENCE);
        assertThat(outcome.confidenceBand()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("confidence < 0.40 ⇒ INCONCLUSIVE; 0.45 vẫn ra kết quả (để demo được R4)")
    void lowConfidenceCrossesTheInconclusiveThreshold() {
        assertThat(factory.fromManualPh(new BigDecimal("6.45"), null, new BigDecimal("0.30"), null)
                .classification()).isEqualTo(ScanClassification.INCONCLUSIVE);

        PipelineOutcome lowButUsable = factory.fromManualPh(
                new BigDecimal("6.45"), null, new BigDecimal("0.45"), null);
        assertThat(lowButUsable.classification()).isEqualTo(ScanClassification.IN_RANGE);
        assertThat(lowButUsable.confidenceBand()).isEqualTo("MEDIUM");
    }

    @Test
    @DisplayName("Không truyền pH ⇒ INCONCLUSIVE (không bịa ra một giá trị mặc định)")
    void missingPhIsInconclusive() {
        PipelineOutcome outcome = factory.fromManualPh(null, null, null, null);

        assertThat(outcome.inconclusive()).isTrue();
        assertThat(outcome.phValue()).isNull();
    }

    @Test
    @DisplayName("flagOf gắn đúng mức độ mà ColorPipelineService dùng cho từng mã")
    void flagSeverityMatchesTheImagePipeline() {
        assertThat(ManualPipelineOutcomeFactory.flagOf(QualityFlagCode.BLURRY))
                .isEqualTo(new QualityFlag(QualityFlagCode.BLURRY, QualityFlagSeverity.BLOCKING));
        assertThat(ManualPipelineOutcomeFactory.flagOf(QualityFlagCode.NO_INDICATOR_GRAINS).severity())
                .isEqualTo(QualityFlagSeverity.BLOCKING);
        assertThat(ManualPipelineOutcomeFactory.flagOf(QualityFlagCode.CARD_NOT_FOUND).severity())
                .isEqualTo(QualityFlagSeverity.WARN);
        assertThat(ManualPipelineOutcomeFactory.flagOf(QualityFlagCode.CALIBRATION_UNAVAILABLE).severity())
                .as("ColorPipelineService gắn WARN cho mã này, javadoc enum ghi BLOCKING — bản chạy thật thắng")
                .isEqualTo(QualityFlagSeverity.WARN);
    }

    /** Chép đúng seed V9 (placeholder chart + 6 band toàn cục). */
    private static final class FixedChartCatalog implements ChartCatalog {

        @Override
        public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
            List<PhChartPoint> points = List.of(
                    new PhChartPoint("00000000-0000-0000-0000-000000000101", 5.5,
                            new Lab(70.0, 20.0, 30.0), 2.0, "#AABBCC", "#AABBCC", "5,5", "5.5", 0, 0.0),
                    new PhChartPoint("00000000-0000-0000-0000-000000000102", 8.0,
                            new Lab(50.0, -10.0, -20.0), 2.0, "#112233", "#112233", "8,0", "8.0", 0, 0.0));
            return Optional.of(new PhChart(CHART_ID.toString(), 1, PhChart.Status.ACTIVE,
                    "D65", 2, true, points, DeltaE2000Params.CAT_CHECK));
        }

        @Override
        public List<PhBandClassifier.Band> findGlobalBands() {
            return List.of(
                    band(PhBandClassifier.Code.LOW, null, 6.0, true, false, PhBandClassifier.Severity.WATCH, 1),
                    band(PhBandClassifier.Code.SLIGHTLY_LOW, 6.0, 6.3, true, false, PhBandClassifier.Severity.ATTENTION, 2),
                    band(PhBandClassifier.Code.IN_RANGE, 6.3, 6.6, true, true, PhBandClassifier.Severity.NORMAL, 3),
                    band(PhBandClassifier.Code.SLIGHTLY_HIGH, 6.6, 7.0, false, true, PhBandClassifier.Severity.ATTENTION, 4),
                    band(PhBandClassifier.Code.HIGH, 7.0, null, false, true, PhBandClassifier.Severity.WATCH, 5),
                    band(PhBandClassifier.Code.INCONCLUSIVE, null, null, true, true, PhBandClassifier.Severity.NEUTRAL, 6));
        }

        private PhBandClassifier.Band band(PhBandClassifier.Code code, Double min, Double max,
                                           boolean minInclusive, boolean maxInclusive,
                                           PhBandClassifier.Severity severity, int sortOrder) {
            return new PhBandClassifier.Band(code, min, max, minInclusive, maxInclusive,
                    code.name(), severity, "color-ph-unknown", "help-circle", sortOrder);
        }
    }
}
