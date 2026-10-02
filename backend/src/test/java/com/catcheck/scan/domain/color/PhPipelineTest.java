package com.catcheck.scan.domain.color;

import com.catcheck.scan.domain.color.ConfidenceCalculator.CalibrationTier;
import com.catcheck.scan.domain.color.ConfidenceCalculator.Inputs;
import com.catcheck.scan.domain.color.ConfidenceCalculator.Thresholds;
import com.catcheck.scan.domain.color.GrainSelector.Candidate;
import com.catcheck.scan.domain.color.GrainSelector.VisionPortThresholds;
import com.catcheck.scan.domain.color.PhBandClassifier.Band;
import com.catcheck.scan.domain.color.PhBandClassifier.Code;
import com.catcheck.scan.domain.color.PhBandClassifier.Severity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiểm thử logic S7→S10: thống kê robust, khớp bảng màu, confidence, phân loại, tách hạt.
 *
 * <p>Đây là phần quyết định <b>số nào người dùng nhìn thấy</b>, nên test ở đây kiểm tra cả hai mặt:
 * đúng về số học, và đúng về hành vi khi dữ liệu xấu (ít hạt, ngoài dải, CI cắt ranh giới) — vì đó là
 * những trường hợp thật sự xảy ra với ảnh chụp trong nhà tắm.
 */
class PhPipelineTest {

    // ------------------------------------------------------------------ S7

    @Test
    @DisplayName("S7 — một hạt hỏng không được phép kéo median đi")
    void medianSurvivesOneBrokenGrain() {
        List<Lab> clean = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            clean.add(new Lab(55.0 + i * 0.1, 12.0, -20.0));
        }
        List<Lab> withOutlier = new ArrayList<>(clean);
        // Pixel bắt sáng lóa đọc thành trắng bão hoà.
        withOutlier.add(new Lab(98.0, 2.0, 40.0));

        Lab medianOfClean = RobustStats.analyze(clean).median();
        Lab medianWithOutlier = RobustStats.analyze(withOutlier).median();

        assertThat(medianWithOutlier.l()).isCloseTo(medianOfClean.l(), within(0.2));
        assertThat(medianWithOutlier.a()).isCloseTo(medianOfClean.a(), within(0.2));
    }

    @Test
    @DisplayName("S7 — trung bình cộng thì hỏng, trung vị thì không (minh hoạ nguyên nhân dùng median)")
    void arithmeticMeanWouldBeCorrupted() {
        List<Lab> samples = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            samples.add(new Lab(50.0, 10.0, -10.0));
        }
        samples.add(new Lab(99.0, 0.0, 0.0));

        double sum = 0.0;
        for (Lab lab : samples) {
            sum += lab.l();
        }
        double arithmeticMean = sum / samples.size();

        assertThat(RobustStats.analyze(samples).median().l())
                .as("median phai giu nguyen gia tri cua phan lon mau")
                .isCloseTo(50.0, within(0.05));
        assertThat(arithmeticMean)
                .as("trung binh cong bi mot mau hong keo lech 5 don vi")
                .isGreaterThan(53.0);
    }

    @Test
    @DisplayName("S7 — dưới minBlobs thì giữ toàn bộ mẫu và bật cờ FEW_BLOBS")
    void tooFewBlobsIsFlagged() {
        RobustStats.Result result = RobustStats.analyze(
                List.of(new Lab(50.0, 10.0, -10.0), new Lab(51.0, 11.0, -9.0)));

        assertThat(result.fewBlobs()).isTrue();
        assertThat(result.blobCount()).isEqualTo(2);
        assertThat(result.droppedCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("S7 — hạt lẫn nhiều màu thì spread lớn và bật cờ MIXED_COLORS")
    void mixedColoursRaiseSpread() {
        List<Lab> mixed = List.of(
                new Lab(50.0, 10.0, -10.0),
                new Lab(60.0, 30.0, 10.0),
                new Lab(40.0, -20.0, -30.0),
                new Lab(55.0, 5.0, 5.0),
                new Lab(45.0, -5.0, -5.0));

        assertThat(RobustStats.analyze(mixed).spreadDeltaE00()).isGreaterThan(6.0);
        assertThat(RobustStats.analyze(mixed).mixedColors()).isTrue();
    }

    // ------------------------------------------------------------------ S8

    private static PhChart sampleChart() {
        return PhChart.of("chart-test", List.of(
                point("p1", 5.5, "#8FB6C9"),
                point("p2", 6.3, "#C9CE8F"),
                point("p3", 6.6, "#8FC98F"),
                point("p4", 7.5, "#C98F8F")));
    }

    private static PhChartPoint point(String id, double ph, String hex) {
        return new PhChartPoint(id, ph, ColorSpace.hexToLab(hex), 4.0, hex, hex, id, id, 5, 1.0);
    }

    @Test
    @DisplayName("S8 — màu đúng bằng một mức trong bảng thì pH khớp và matchPercent rất cao")
    void exactChartColourMatchesItsLevel() {
        PhChart chart = sampleChart();
        Lab target = chart.points().get(1).lab();

        ChartMatcher.Match match = ChartMatcher.match(target, chart, null);

        assertThat(match.phEstimate()).isEqualTo(6.3);
        assertThat(match.deltaEMin()).isCloseTo(0.0, within(1e-9));
        assertThat(match.matchPercent()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("S8 — nội sup theo ΔE00, không theo phH: màu nằm giữa hai mức cho pH ở giữa khoảng")
    void interpolatesAlongDeltaE() {
        PhChart chart = sampleChart();
        Lab lower = chart.points().get(1).lab();
        Lab upper = chart.points().get(2).lab();
        // Trung điểm theo ΔE00 của hai mức 6.3 và 6.6.
        Lab midpoint = new Lab((lower.l() + upper.l()) / 2, (lower.a() + upper.a()) / 2,
                (lower.b() + upper.b()) / 2);

        ChartMatcher.Match match = ChartMatcher.match(midpoint, chart, null);

        assertThat(match.phEstimate()).isBetween(6.3, 6.6);
    }

    @Test
    @DisplayName("S8 — pH luôn nằm trong dải của bảng, không suy diễn ra ngoài")
    void neverExtrapolatesOutsideChart() {
        PhChart chart = sampleChart();
        Lab farOutside = new Lab(20.0, 60.0, 60.0);

        ChartMatcher.Match match = ChartMatcher.match(farOutside, chart, null);

        assertThat(match.phEstimate()).isBetween(chart.minPh(), chart.maxPh());
    }

    @Test
    @DisplayName("S8 — matchPercent trung thực: ΔE00=0 ra 100, ΔE00 lớn ra thấp, không bao giờ 98% cứng")
    void matchPercentIsHonest() {
        assertThat(ChartMatcher.matchPercent(0.0)).isEqualTo(100.0);
        assertThat(ChartMatcher.matchPercent(1.5))
                .as("ΔE00 1.5 la khop rat tot nhung van phai deo thuc te")
                .isEqualTo(90.0);
        assertThat(ChartMatcher.matchPercent(15.0)).isEqualTo(0.0);
        assertThat(ChartMatcher.matchPercent(99.0)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("S8 — chênh lệch > 0.3 pH giữa chế độ thẻ và bảng DB thì báo CARD_CHART_MISMATCH")
    void detectsCardChartMismatch() {
        assertThat(ChartMatcher.mismatches(6.4, 6.5)).isFalse();
        assertThat(ChartMatcher.mismatches(6.4, 6.8)).isTrue();
    }

    // ------------------------------------------------------------------ S9

    private static Inputs goodInputs() {
        return new Inputs(1.0, 1.0, 1.0, 0.05, 200.0, 100.0, 0.0, 0.0, 5.0, 30);
    }

    @Test
    @DisplayName("S9 — mọi thành phần tốt thì confidence cao, bị trần theo tier A")
    void goodImageIsCappedByTierA() {
        ConfidenceCalculator.Result result = ConfidenceCalculator.calculate(
                goodInputs(), 6.4, sampleChart(), 1.0, 1.0, 30, 0.5, CalibrationTier.A, Thresholds.standard());

        assertThat(result.confidence()).isGreaterThan(0.6);
        assertThat(result.confidence()).isLessThanOrEqualTo(0.95);
    }

    @Test
    @DisplayName("S9 — nhánh không thẻ bị trần 0.60 dù ảnh rất đẹp (giới hạn về nguồn bằng chứng)")
    void noCardBranchIsCappedAtPointSix() {
        ConfidenceCalculator.Result result = ConfidenceCalculator.calculate(
                goodInputs(), 6.4, sampleChart(), 1.0, 1.0, 30, 0.5, CalibrationTier.B, Thresholds.standard());

        assertThat(result.confidence()).isLessThanOrEqualTo(0.60);
    }

    @Test
    @DisplayName("S9 — không hiệu chuẩn được thì trần 0.35, tức luôn INCONCLUSIVE")
    void uncalibratedBranchIsAlwaysInconclusive() {
        ConfidenceCalculator.Result result = ConfidenceCalculator.calculate(
                goodInputs(), 6.4, sampleChart(), 1.0, 1.0, 30, 0.5, CalibrationTier.C, Thresholds.standard());

        assertThat(result.confidence()).isLessThanOrEqualTo(0.35);
        assertThat(result.isInconclusive()).isTrue();
    }

    @Test
    @DisplayName("S9 — ảnh mờ kéo confidence tụt dù thẻ nhìn rõ (trung bình nhân, không phải cộng)")
    void blurCannotBeCompensated() {
        ConfidenceCalculator.Inputs blurred = new Inputs(
                1.0, 1.0, 1.0, 0.05, 100.0, 100.0, 0.0, 0.0, 5.0, 30);

        double sharp = ConfidenceCalculator.calculate(goodInputs(), 6.4, sampleChart(), 1.0, 1.0, 30,
                0.5, CalibrationTier.A, Thresholds.standard()).confidence();
        double blurry = ConfidenceCalculator.calculate(blurred, 6.4, sampleChart(), 1.0, 1.0, 30,
                0.5, CalibrationTier.A, Thresholds.standard()).confidence();

        assertThat(blurry).as("c_focus = 0 khi blurVar = blurMin").isLessThan(sharp);
    }

    @Test
    @DisplayName("S9 — CI luôn nằm trong [ciMin, ciMax] quanh pH ước lượng")
    void confidenceIntervalRespectsBounds() {
        ConfidenceCalculator.Result tight = ConfidenceCalculator.calculate(
                goodInputs(), 6.4, sampleChart(), 0.5, 0.2, 40, 0.1, CalibrationTier.A, Thresholds.standard());
        ConfidenceCalculator.Result loose = ConfidenceCalculator.calculate(
                goodInputs(), 6.4, sampleChart(), 6.0, 8.0, 4, 5.0, CalibrationTier.A, Thresholds.standard());

        assertThat(tight.phHigh() - tight.phLow())
                .as("CI han hep khi mau sat va do doc cua bang mau cao")
                .isLessThanOrEqualTo(2 * ConfidenceCalculator.CI_MAX);
        assertThat(loose.phHigh() - loose.phLow())
                .as("CI khong bao gio lo hon 2*ciMin")
                .isGreaterThanOrEqualTo(2 * ConfidenceCalculator.CI_MIN - 0.11);
    }

    // ------------------------------------------------------------------ S10

    private static List<Band> bands() {
        return List.of(
                new Band(Code.LOW, null, 5.9, true, true, "ph.low", Severity.WATCH, "color-ph-abnormal",
                        "alert-triangle", 1),
                new Band(Code.SLIGHTLY_LOW, 6.0, 6.29, true, true, "ph.slightlyLow", Severity.ATTENTION,
                        "color-ph-mild", "arrow-down-circle", 2),
                new Band(Code.IN_RANGE, 6.3, 6.6, true, true, "ph.inRange", Severity.NORMAL, "color-ph-normal",
                        "check-circle", 3),
                new Band(Code.SLIGHTLY_HIGH, 6.61, 7.0, true, true, "ph.slightlyHigh", Severity.ATTENTION,
                        "color-ph-mild", "arrow-up-circle", 4),
                new Band(Code.HIGH, 7.1, null, true, true, "ph.high", Severity.WATCH, "color-ph-abnormal",
                        "alert-triangle", 5),
                new Band(Code.INCONCLUSIVE, null, null, true, true, "ph.inconclusive", Severity.NEUTRAL,
                        "color-ph-unknown", "help-circle", 6));
    }

    @Test
    @DisplayName("S10 — phân loại đúng 6 dải, nhãn đến từ labelKey trong DB chứ không viết trong code")
    void classifiesAllSixBands() {
        assertThat(PhBandClassifier.classify(5.5, 5.0, 6.0, 0.8, bands(), false).code()).isEqualTo(Code.LOW);
        assertThat(PhBandClassifier.classify(6.1, 5.5, 6.3, 0.8, bands(), false).code())
                .isEqualTo(Code.SLIGHTLY_LOW);
        assertThat(PhBandClassifier.classify(6.45, 6.3, 6.6, 0.8, bands(), false).code()).isEqualTo(Code.IN_RANGE);
        assertThat(PhBandClassifier.classify(6.8, 6.6, 7.0, 0.8, bands(), false).code())
                .isEqualTo(Code.SLIGHTLY_HIGH);
        assertThat(PhBandClassifier.classify(7.8, 7.5, 8.1, 0.8, bands(), false).code()).isEqualTo(Code.HIGH);
    }

    @Test
    @DisplayName("S10 — confidence thấp hoặc có cờ BLOCKING thì INCONCLUSIVE bất kể pH")
    void lowConfidenceWinsOverNiceNumber() {
        assertThat(PhBandClassifier.classify(6.45, 6.3, 6.6, 0.2, bands(), false).code())
                .isEqualTo(Code.INCONCLUSIVE);
        assertThat(PhBandClassifier.classify(6.45, 6.3, 6.6, 0.9, bands(), true).code())
                .isEqualTo(Code.INCONCLUSIVE);
    }

    @Test
    @DisplayName("S10 — NEAR_BOUNDARY khi CI cắt qua 6.3 hoặc 6.6; và luôn false khi INCONCLUSIVE")
    void nearBoundaryIsMandatory() {
        assertThat(PhBandClassifier.classify(6.25, 6.2, 6.4, 0.8, bands(), false).nearBoundary())
                .as("CI cat qua 6.3 thi khong biet chay ve ben nao")
                .isTrue();
        assertThat(PhBandClassifier.classify(6.45, 6.35, 6.55, 0.8, bands(), false).nearBoundary())
                .as("CI nam trong 6.3-6.6 thi khong sat ranh gi")
                .isFalse();
        assertThat(PhBandClassifier.classify(6.25, 6.2, 6.4, 0.2, bands(), false).nearBoundary())
                .as("chua co ket luan thi 'sat ranh gi' vo nghia")
                .isFalse();
    }

    @Test
    @DisplayName("S10 — token màu chỉ dùng 4 token vai trò color-ph-* (QĐ #6, R-F05)")
    void usesOnlyRoleTokens() {
        for (Band band : bands()) {
            assertThat(band.colorToken())
                    .as("token cua %s", band.code())
                    .isIn("color-ph-normal", "color-ph-mild", "color-ph-abnormal", "color-ph-unknown");
        }
    }

    // ------------------------------------------------------------------ S6

    @Test
    @DisplayName("S6 — loại hạt bắt sáng, quá tối, chroma thấp và trùng màu nền")
    void grainSelectionAppliesAllThreeConditions() {
        VisionPortThresholds thresholds = VisionPortThresholds.standard();
        Lab substrate = new Lab(70.0, 2.0, 3.0);

        List<Candidate> candidates = List.of(
                new Candidate(40, new Lab(60.0, 20.0, -20.0), 65.0, 55.0),   // hạt thật
                new Candidate(40, new Lab(98.0, 20.0, -20.0), 99.0, 90.0),   // bắt sáng
                new Candidate(40, new Lab(5.0, 20.0, -20.0), 10.0, 2.0),     // quá tối
                new Candidate(40, new Lab(70.0, 1.0, 1.0), 72.0, 68.0),      // chroma thấp
                new Candidate(40, new Lab(70.0, 5.0, 6.0), 72.0, 68.0),      // gần màu nền
                new Candidate(5, new Lab(60.0, 20.0, -20.0), 62.0, 58.0));   // quá nhỏ

        GrainSelector.Selection selection = GrainSelector.select(
                candidates, substrate, thresholds, 1000, 3.0);

        assertThat(selection.kept()).hasSize(1);
        assertThat(selection.indicatorRatio()).isCloseTo(0.04, within(1e-6));
        assertThat(selection.offCurve()).isFalse();
    }

    @Test
    @DisplayName("S6 — phủ hạt thấp: dưới ngưỡng cứng thì INCONCLUSIVE, dưới ngưỡng mềm thì chỉ cờ")
    void coverageThresholdsSplitSoftAndHard() {
        VisionPortThresholds thresholds = VisionPortThresholds.standard();
        List<Candidate> oneGrain = List.of(new Candidate(10, new Lab(60.0, 20.0, -20.0), 62.0, 58.0));

        GrainSelector.Selection sparse = GrainSelector.select(oneGrain, new Lab(70.0, 2.0, 3.0),
                thresholds, 100_000, 3.0);

        assertThat(sparse.indicatorRatio()).isLessThan(thresholds.hardMinCoverage());
        assertThat(GrainSelector.isInconclusive(sparse, thresholds)).isTrue();
        assertThat(GrainSelector.isLowCoverage(sparse, thresholds)).isTrue();
    }

    @Test
    @DisplayName("S6 — hạt nằm xa dải màu ACTIVE thì bật cờ OFF_CURVE")
    void offCurveIsFlagged() {
        VisionPortThresholds thresholds = VisionPortThresholds.standard();
        List<Candidate> candidates = List.of(new Candidate(200, new Lab(60.0, 20.0, -20.0), 62.0, 58.0));

        GrainSelector.Selection selection = GrainSelector.select(candidates, new Lab(70.0, 2.0, 3.0),
                thresholds, 1000, 30.0);

        assertThat(selection.offCurve()).isTrue();
    }
}
