package com.catcheck.scan.domain.color;

import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.color.port.VisionEngine.QuadHint;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Kiểm thử cho phần sửa đặc tả: CI dùng đúng residual, trim S7, sắp xếp quad, WB, linearization. */
class PhPipelineEdgeTest {

    private static PhChart chart() {
        return PhChart.of("chart-test", List.of(
                point("p1", 4.5, "#8FB6C9"),
                point("p2", 5.5, "#A8C4A0"),
                point("p3", 6.5, "#C9CE8F"),
                point("p4", 7.5, "#C98F8F"),
                point("p5", 8.5, "#8F5FC9")));
    }

    private static PhChartPoint point(String id, double ph, String hex) {
        return new PhChartPoint(id, ph, ColorSpace.hexToLab(hex), 4.0, hex, hex, id, id, 5, 1.0);
    }

    @Nested
    @DisplayName("S9 — khoảng tin cậy")
    class ConfidenceInterval {

        @Test
        @DisplayName("sigmaDeltaE lay residual hieu chuan S4 chu khong phai residual vuong goc")
        void ciUsesCalibrationResidualNotPerpendicularResidual() {
            ConfidenceCalculator.Thresholds thresholds = ConfidenceCalculator.Thresholds.standard();
            // Mọi thành phần chất lượng tốt, chỉ khác residual: S4 = 3.0, vuông góc = 0.0.
            ConfidenceCalculator.Inputs good = new ConfidenceCalculator.Inputs(
                    3.0, 2.0, 2.0, 0.02, 200.0, 100.0, 0.0, 0.0, 0.0, 30);

            var withPerpZero = ConfidenceCalculator.calculate(
                    good, 6.5, chart(), 2.0, 2.0, 30, 0.0, ConfidenceCalculator.CalibrationTier.A, thresholds);
            var withPerpLarge = ConfidenceCalculator.calculate(
                    good, 6.5, chart(), 2.0, 2.0, 30, 5.0, ConfidenceCalculator.CalibrationTier.A, thresholds);

            double widthZero = withPerpZero.phHigh() - withPerpZero.phLow();
            double widthLarge = withPerpLarge.phHigh() - withPerpLarge.phLow();

            assertThat(widthLarge)
                    .as("residual vuong goc lon phai lam CI rong hon")
                    .isGreaterThan(widthZero);

            // Ngược lại: residual S4 tăng thì CI phai rong ra, ke ca khi vuong goc = 0.
            ConfidenceCalculator.Inputs worseResidual = new ConfidenceCalculator.Inputs(
                    6.0, 2.0, 2.0, 0.02, 200.0, 100.0, 0.0, 0.0, 0.0, 30);
            var residualWorse = ConfidenceCalculator.calculate(
                    worseResidual, 6.5, chart(), 2.0, 2.0, 30, 0.0,
                    ConfidenceCalculator.CalibrationTier.A, thresholds);

            assertThat(residualWorse.phHigh() - residualWorse.phLow())
                    .as("residual hieu chuan S4 tang se lam CI rong ra")
                    .isGreaterThan(widthZero);
        }

        @Test
        @DisplayName("bán rộng CI luôn nằm trong khoang [ciMin, ciMax] ke ca khi gradient bằng 0")
        void ciHalfWidthIsClamped() {
            ConfidenceCalculator.Inputs inputs = new ConfidenceCalculator.Inputs(
                    50.0, 50.0, 50.0, 0.001, 100.0, 100.0, 0.5, 0.5, 200.0, 1);
            var result = ConfidenceCalculator.calculate(inputs, 6.5, chart(), 50.0, 50.0, 1, 50.0,
                    ConfidenceCalculator.CalibrationTier.C, ConfidenceCalculator.Thresholds.standard());

            double halfWidth = result.phHigh() - 6.5;
            assertThat(halfWidth).isLessThanOrEqualTo(ConfidenceCalculator.CI_MAX + 1e-9);
            assertThat(halfWidth).isGreaterThanOrEqualTo(ConfidenceCalculator.CI_MIN - 1e-9);
        }

        @Test
        @DisplayName("tier C khong bao gio vuot tran 0.35 va bi coi la inconclusive")
        void tierCapsAreEnforced() {
            ConfidenceCalculator.Inputs perfect = new ConfidenceCalculator.Inputs(
                    0.5, 0.5, 0.5, 0.05, 500.0, 100.0, 0.0, 0.0, 0.0, 40);
            var tierC = ConfidenceCalculator.calculate(perfect, 6.5, chart(), 0.5, 0.5, 40, 0.0,
                    ConfidenceCalculator.CalibrationTier.C, ConfidenceCalculator.Thresholds.standard());
            var tierA = ConfidenceCalculator.calculate(perfect, 6.5, chart(), 0.5, 0.5, 40, 0.0,
                    ConfidenceCalculator.CalibrationTier.A, ConfidenceCalculator.Thresholds.standard());

            assertThat(tierC.confidence()).isLessThanOrEqualTo(0.35);
            assertThat(tierC.isInconclusive()).isTrue();
            assertThat(tierA.confidence()).isGreaterThan(tierC.confidence());
        }
    }

    @Nested
    @DisplayName("S7 — trim 10% moi duoi")
    class Trim {

        @Test
        @DisplayName("du 10 mau thi trim co tac dung; duoi nguong thi giu nguyen")
        void trimOnlyAppliesAboveThreshold() {
            List<Lab> ten = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ten.add(new Lab(50.0, 0.0, 0.0));
            }
            ten.add(new Lab(40.0, 0.0, 0.0));
            ten.add(new Lab(45.0, 0.0, 0.0));

            List<Lab> nine = new ArrayList<>(ten.subList(1, 10));

            // 9 hạt: hai hạt lạc bị loại, phần còn lại đồng nhất nên labMedian = 50.
            assertThat(RobustStats.analyze(nine).median().l()).isCloseTo(50.0, within(0.01));
            assertThat(RobustStats.analyze(nine).blobCount()).isEqualTo(7);
            assertThat(RobustStats.analyze(nine).droppedCount()).isEqualTo(2);

            // Dưới 10 mẫu thì trim = 10% làm tròn về 0, tức trimmed mean trùng mean thường —
            // đặc tả cắt "10% mỗi đuôi" chứ không hứa cắt được trên tập nhỏ.
            assertThat(RobustStats.trimmedMeanPerChannel(nine, RobustStats.TRIM).l())
                    .as("9 mau: cut = floor(0.9) = 0 nen khong cat gi")
                    .isCloseTo(48.333, within(1e-3));
        }

        @Test
        @DisplayName("trim gioi han anh huong cua mot gia tri dot bien")
        void trimmedMeanBoundsExtremeInfluence() {
            double[] clean = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100};
            double[] polluted = new double[clean.length + 1];
            System.arraycopy(clean, 0, polluted, 0, clean.length);
            polluted[clean.length] = 10_000.0;

            double plainShift = Math.abs(plainMean(polluted) - plainMean(clean));
            double trimmedShift = Math.abs(
                    RobustStats.trimmedMean(polluted, 0.10) - RobustStats.trimmedMean(clean, 0.10));

            assertThat(plainMean(clean)).isCloseTo(55.0, within(1e-9));
            assertThat(trimmedShift)
                    .as("mot hat cat sai lam mean bay, trim cat con 5 don vi")
                    .isLessThan(plainShift / 50.0);
        }

        @Test
        @DisplayName("trim dung cong thuc: cut = floor(n * trim) o moi duoi")
        void trimmedMeanIsExact() {
            double[] values = {0, 0, 0, 0, 0, 0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140};
            assertThat(plainMean(values)).isCloseTo(52.5, within(1e-9));
            // cut = floor(20 * 0.10) = 2 -> bo 2 o duoi dau va 2 o duoi cuoi.
            assertThat(RobustStats.trimmedMean(values, 0.10)).isCloseTo(48.75, within(1e-9));
        }

        private double plainMean(double[] values) {
            return java.util.Arrays.stream(values).average().orElseThrow();
        }

        @Test
        @DisplayName("S7 dung trimmed mean cho lab_median, khong dung median")
        void aggregateIsTrimmedMean() {
            List<Lab> samples = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                samples.add(new Lab(50.0 + i, 0.0, 0.0));
            }
            RobustStats.Result result = RobustStats.analyze(samples);
            double plainMean = samples.stream().mapToDouble(Lab::l).average().orElseThrow();
            assertThat(RobustStats.medianPerChannel(samples).l()).isEqualTo(59.5);
            assertThat(result.median().l())
                    .as("trimmed mean cua 50..69 = 59.5, trim cat 2 o moi duoi")
                    .isCloseTo(plainMean, within(6.0));
        }
    }

    @Nested
    @DisplayName("S2 — sap xep quad")
    class QuadOrdering {

        @Test
        @DisplayName("mọi hoan vi dau vao deu ra cung mot thu tu chuan")
        void canonicalOrderIsPermutationInvariant() {
            List<double[]> corners = List.of(
                    new double[] {10, 10}, new double[] {110, 12}, new double[] {108, 63}, new double[] {12, 61});

            QuadHint reference = CardQuadOrdering.canonicalize(corners);
            assertThat(reference.topLeft()).containsExactly(10.0, 10.0);
            assertThat(reference.topRight()).containsExactly(110.0, 12.0);
            assertThat(reference.bottomRight()).containsExactly(108.0, 63.0);
            assertThat(reference.bottomLeft()).containsExactly(12.0, 61.0);

            // Thứ tự quanh chu vi sau khi chuẩn hoá phải thuận chiều kim đồng hồ trên màn hình.
            assertThat(CardQuadOrdering.signedArea(reference))
                    .as("dien tich duong chung to bay thu tu doc la TL -> TR -> BR -> BL")
                    .isCloseTo(98.0 * 51.0, within(1.0));

            // Hoán vị: thứ tự vào khác nhau phai cho cung mot quad.
            List<double[]> rotated = List.of(
                    corners.get(2), corners.get(3), corners.get(0), corners.get(1));
            assertThat(CardQuadOrdering.canonicalize(rotated).topLeft()).isEqualTo(reference.topLeft());

            List<double[]> reversed = List.of(
                    corners.get(3), corners.get(1), corners.get(0), corners.get(2));
            assertThat(CardQuadOrdering.canonicalize(reversed).topRight()).isEqualTo(reference.topRight());
        }

        @Test
        @DisplayName("the xoay 40 do van ra dung thu tu")
        void handlesRotation() {
            double[][] unrotated = {{0, 0}, {100, 0}, {100, 64}, {0, 64}};
            double angle = Math.toRadians(40.0);
            List<double[]> rotated = new ArrayList<>();
            for (double[] point : unrotated) {
                rotated.add(new double[] {
                        point[0] * Math.cos(angle) - point[1] * Math.sin(angle),
                        point[0] * Math.sin(angle) + point[1] * Math.cos(angle)});
            }

            QuadHint quad = CardQuadOrdering.canonicalize(rotated);
            // Sau khi xoay, goc tren-trai moi la diem goc, goc duoi-phai doi lau nhat.
            assertThat(quad.topLeft()[0] + quad.topLeft()[1])
                    .as("topLeft phai la diem nho nhat theo x+y")
                    .isLessThanOrEqualTo(Math.min(
                            quad.topRight()[0] + quad.topRight()[1],
                            quad.bottomLeft()[0] + quad.bottomLeft()[1]));
            assertThat(Math.abs(CardQuadOrdering.signedArea(quad)))
                    .as("dien tich khong doi khi xoay")
                    .isCloseTo(100.0 * 64.0, within(1e-6));
        }

        @Test
        @DisplayName("sai so dinh so ve thieu nhan goc thi nem loi, khong doan bua")
        void rejectsWrongPointCount() {
            assertThatThrownBy(() -> CardQuadOrdering.canonicalize(List.of(new double[] {0, 0})))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("4 dinh");
        }

        @Test
        @DisplayName("the meo nang bi loi vi ti le canh lech qua xa")
        void rejectsImplausibleShapes() {
            QuadHint thin = new QuadHint(new double[] {0, 0}, new double[] {200, 0},
                    new double[] {200, 2}, new double[] {0, 2});
            assertThat(CardQuadOrdering.isPlausible(thin, CardQuadOrdering.MIN_CARD_AREA_PX)).isFalse();

            QuadHint tiny = new QuadHint(new double[] {0, 0}, new double[] {10, 0},
                    new double[] {10, 10}, new double[] {0, 10});
            assertThat(CardQuadOrdering.isPlausible(tiny, CardQuadOrdering.MIN_CARD_AREA_PX)).isFalse();

            QuadHint good = new QuadHint(new double[] {0, 0}, new double[] {200, 0},
                    new double[] {200, 130}, new double[] {0, 130});
            assertThat(CardQuadOrdering.isPlausible(good, CardQuadOrdering.MIN_CARD_AREA_PX)).isTrue();
        }
    }

    @Nested
    @DisplayName("S4 — can bang trang")
    class WhiteBalance {

        @Test
        @DisplayName("von Kries dua diem tham chieu ve trung tinh")
        void vonKriesNeutralisesReference() {
            RgbLinear castUnderWarmLight = new RgbLinear(0.40, 0.35, 0.25);
            var gains = WhiteBalanceSolver.fromNeutralPoint(castUnderWarmLight);
            RgbLinear corrected = gains.apply(castUnderWarmLight);

            assertThat(corrected.mean()).isCloseTo(castUnderWarmLight.mean(), within(1e-9));
            assertThat(WhiteBalanceSolver.MAX_GAIN).isGreaterThan(gains.b());
            assertThat(gains.r()).isLessThan(gains.b());
        }

        @Test
        @DisplayName("gain vuot dai bi kep de khong pha hong anh")
        void gainsAreClamped() {
            var gains = WhiteBalanceSolver.fromNeutralPoint(new RgbLinear(1.0, 1.0, 0.01));
            assertThat(gains.b())
                    .as("chanh lech 100 lan giua B va trung binh la khong phai nhieu sang, ma la the hong")
                    .isEqualTo(WhiteBalanceSolver.MAX_GAIN);
            assertThat(gains.r()).isCloseTo(0.67, within(1e-9));
            for (double gain : gains.toArray()) {
                assertThat(gain).isBetween(WhiteBalanceSolver.MIN_GAIN, WhiteBalanceSolver.MAX_GAIN);
            }
        }

        @Test
        @DisplayName("diem tham chieu qua toi thi nem loi thay vi tra ve gain vo nghia")
        void blackReferenceIsRejected() {
            assertThatThrownBy(() -> WhiteBalanceSolver.fromNeutralPoint(new RgbLinear(0, 0, 0)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("qua toi");
        }

        @Test
        @DisplayName("nhanh du phong pha 50/50 gray-world voi white-patch p95")
        void fallbackBlendsTwoEstimators() {
            List<RgbLinear> background = List.of(
                    new RgbLinear(0.50, 0.50, 0.50),
                    new RgbLinear(0.50, 0.50, 0.50),
                    new RgbLinear(0.40, 0.40, 0.40),
                    new RgbLinear(0.90, 0.90, 0.90),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30),
                    new RgbLinear(0.30, 0.30, 0.30));

            var gains = WhiteBalanceSolver.fallback(background);
            // Nen xam: ca hai uoc luong deu trung tinh nen gain phai ve 1.
            for (double gain : gains.toArray()) {
                assertThat(gain).isCloseTo(1.0, within(0.05));
            }
        }
    }

    @Nested
    @DisplayName("S4 — duong cong tuyen tinh hoa")
    class Linearization {

        @Test
        @DisplayName("fit bac 3 khoi phuc duoc duong cong sRGB that")
        void fitRecoversSrgbCurve() {
            double[] encoded = {0.0, 0.25, 0.5, 0.75, 1.0};
            double[] reference = new double[encoded.length];
            for (int i = 0; i < encoded.length; i++) {
                reference[i] = ColorSpace.srgbToLinear(encoded[i]);
            }

            LinearizationCurve.GrayPoly3 poly = LinearizationCurve.fit(encoded, reference);

            // sRGB là hàm hai nhánh: tuyến tính dưới 0.04045, lũy thừa 2.4 phía trên. Một đa thức bậc 3
            // không thể khớp cả hai nhánh, nên sai số ở đây là sai số xấp xỉ hợp lệ, không phải lỗi fit.
            for (int i = 0; i < encoded.length; i++) {
                assertThat(poly.evaluate(encoded[i]))
                        .as("fit phai khop duong cong tai chinh diem mau")
                        .isCloseTo(reference[i], within(2e-3));
            }
            assertThat(LinearizationCurve.measured(poly).isMonotonic()).isTrue();
            assertThat(poly.evaluate(1.0) - poly.evaluate(0.0))
                    .as("sRGB la mapping [0,1] -> [0,1]")
                    .isCloseTo(1.0, within(2e-3));
        }

        @Test
        @DisplayName("da thuc khong don dieu thi roi ve sRGB chuan")
        void nonMonotonicFallsBackToStandard() {
            // Da thuc bac 3 quay dau: tang o hai dau, lom o giua.
            var squiggle = new LinearizationCurve.GrayPoly3(0.1, 4.0, 0.0, -2.0);
            var resolved = LinearizationCurve.resolveOrStandard(squiggle);

            assertThat(resolved.type()).isEqualTo(LinearizationCurve.Type.STANDARD_SRGB);
            assertThat(resolved.linearize(0.5)).isCloseTo(ColorSpace.srgbToLinear(0.5), within(1e-9));
        }

        @Test
        @DisplayName("fit du lieu hoi/hang thi khong duoc nem loi im lang ma phai roi ve sRGB")
        void degenerateRampFallsBack() {
            var allZero = LinearizationCurve.fitOrStandard(
                    new double[] {0.0, 0.0, 0.0, 0.0}, new double[] {0.0, 0.1, 0.2, 0.3});
            assertThat(allZero.type()).isEqualTo(LinearizationCurve.Type.STANDARD_SRGB);
        }

        @Test
        @DisplayName("it o xam hon 4 thi khong fit duoc, phai dung duong cong chuan")
        void tooFewPatchesFallsBack() {
            var curve = LinearizationCurve.fitOrStandard(
                    new double[] {0.1, 0.9}, new double[] {0.01, 0.8});
            assertThat(curve.type()).isEqualTo(LinearizationCurve.Type.STANDARD_SRGB);
            assertThatThrownBy(() -> LinearizationCurve.fit(new double[] {0.1, 0.9}, new double[] {0.1, 0.9}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("VisionEngine — hop dong")
    class VisionContract {

        @Test
        @DisplayName("the khong tim thay phai bao ro qua method NONE chu khong tra null")
        void notFoundIsExplicit() {
            var notFound = new VisionEngine.CardDetectionResult(
                    VisionEngine.QuadHint.empty(), null, VisionEngine.CardDetectionResult.Method.NONE);
            assertThat(notFound.found()).isFalse();
            assertThat(notFound.isUsable()).isFalse();

            var found = new VisionEngine.CardDetectionResult(
                    VisionEngine.QuadHint.empty(), null, VisionEngine.CardDetectionResult.Method.ARUCO);
            assertThat(found.found()).isTrue();
            assertThat(found.isUsable())
                    .as("tim thay nhung khong nang phang = CARD_INVALID, van phai roi ve S4b")
                    .isFalse();
        }

        @Test
        @DisplayName("quad rong la gia tri mac dinh, khong phai loi")
        void emptyQuadIsWellFormed() {
            assertThat(VisionEngine.QuadHint.empty().isEmpty()).isTrue();
            assertThatThrownBy(() -> new VisionEngine.QuadHint(new double[] {1, 2}, null,
                    new double[] {3, 4}, new double[] {5, 6}))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
