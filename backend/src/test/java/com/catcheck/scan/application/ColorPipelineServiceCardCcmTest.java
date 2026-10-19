package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.CardLayoutCatalog;
import com.catcheck.scan.domain.color.CcmSolver;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.ColorSamples;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.scan.domain.color.RgbLinear;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.scan.infrastructure.vision.OpenCvNativeLoader;
import com.catcheck.scan.infrastructure.vision.OpenCvRawImageProcessor;
import com.catcheck.scan.infrastructure.vision.OpenCvVisionEngine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ColorPipelineServiceCardCcmTest {

    private static final int CARD_WIDTH = 600;
    private static final int CARD_HEIGHT = 380;

    private static final double CLEAN_INSET_FRAC = 0.08;

    private static final int[][] MEASURED_OFFSET = {
            {6, -4, 2}, {-3, 5, -5}, {4, 3, -6}, {-5, -2, 4}, {2, -6, 3}
    };

    private static final int SAND_CANVAS = 700;
    private static final int GRAIN_DIAMETER = 14;
    private static final int GRAINS_PER_IMAGE = 60;
    private static final int BACKGROUND_GRAY = 185;
    private static final int NOISE_AMPLITUDE = 8;
    private static final String GRAIN_HEX = "#8FC98F";

    private static OpenCvNativeLoader nativeLoader;

    @BeforeAll
    static void loadNative() {
        nativeLoader = new OpenCvNativeLoader();
        invokePostConstruct(nativeLoader);
        assumeTrue(nativeLoader.isAvailable(),
                "OpenCV native khong nap duoc trong moi truong nay - bo qua test can Mat that");
    }

    @Test
    @DisplayName("Patch NEUTRAL hoan hao (khong lech kenh do duoc) -> du lieu do thang hang tuyet doi -> CcmSolver nem loi suy bien -> CARD_UNUSABLE")
    void perfectlyNeutralPatches_measuredDataCollinear_ccmSolverRejects() throws IOException {
        CardLayout layout = buildCardLayout();
        byte[] cardBytes = buildCardImageBytes(layout, false);
        byte[] sandBytes = buildSandImageBytes();

        PipelineOutcome outcome = runPipeline(layout, cardBytes, sandBytes, code -> Optional.of(layout));

        assertThat(outcome.calibrationMethod())
                .as("he CCM suy bien (CcmSolver nem loi) -> KHONG duoc dung CARD_CCM")
                .isNotEqualTo(CalibrationMethod.CARD_CCM);
        assertThat(outcome.qualityFlags())
                .as("the tim thay nhung CCM khong giai duoc -> phai co CARD_UNUSABLE")
                .anyMatch(f -> f.code() == QualityFlagCode.CARD_UNUSABLE);
    }

    @Test
    @DisplayName("Patch NEUTRAL co nhieu do rieng kenh -> CcmSolver giai duoc nhung ma tran suy bien ve truc xam (det=0) -> van CARD_UNUSABLE")
    void neutralPatchesWithNoise_solvedCcmStillSingularByConstruction_rejectedByDeterminantGuard()
            throws IOException {
        CardLayout layout = buildCardLayout();
        byte[] cardBytes = buildCardImageBytes(layout, true);
        byte[] sandBytes = buildSandImageBytes();

        PipelineOutcome outcome = runPipeline(layout, cardBytes, sandBytes, code -> Optional.of(layout));

        assertThat(outcome.calibrationMethod())
                .as("ma tran suy bien (det~0) -> KHONG duoc dung CARD_CCM du residual tren tap train thap")
                .isNotEqualTo(CalibrationMethod.CARD_CCM);
        assertThat(outcome.qualityFlags())
                .as("the tim thay, CCM giai duoc nhung suy bien -> phai co CARD_UNUSABLE")
                .anyMatch(f -> f.code() == QualityFlagCode.CARD_UNUSABLE);
    }

    @Test
    @DisplayName("The dung duoc nhung CHUA publish layout (CardLayoutCatalog rong) -> roi ve SUBSTRATE_WB, khong co CARD_UNUSABLE")
    void cardFoundButNoActiveLayout_fallsBackWithoutFlag() throws IOException {
        CardLayout layout = buildCardLayout();
        byte[] cardBytes = buildCardImageBytes(layout, true);
        byte[] sandBytes = buildSandImageBytes();

        PipelineOutcome outcome = runPipeline(layout, cardBytes, sandBytes, code -> Optional.empty());

        assertThat(outcome.calibrationMethod())
                .as("khong co layout active -> khong the thu CCM, roi ve nhu khong co the")
                .isNotEqualTo(CalibrationMethod.CARD_CCM);
        assertThat(outcome.qualityFlags())
                .as("khong co layout KHONG phai loi cua the - khong duoc gan CARD_UNUSABLE")
                .noneMatch(f -> f.code() == QualityFlagCode.CARD_UNUSABLE);
    }

    @Test
    @DisplayName("CHUNG MINH: CcmSolver.solve tren toan patch NEUTRAL (ref_lab trung tinh) luon tra ve ma tran det=0")
    void solvedNeutralOnlyCcmIsAlwaysSingular_mathematicalProof() {
        List<Lab> neutralRefLabs = List.of(
                new Lab(95.494, 0, 0), new Lab(82.046, 0, 0), new Lab(53.585, 0, 0),
                new Lab(21.247, 0, 0), new Lab(5.063, 0, 0));
        var samples = new java.util.ArrayList<ColorSamples.Sample>();
        Random rng = new Random(1L);
        for (Lab refLab : neutralRefLabs) {
            RgbLinear reference = ColorSpace.labToLinearRgb(refLab);
            RgbLinear measured = new RgbLinear(
                    reference.r() + rng.nextGaussian() * 0.02,
                    reference.g() + rng.nextGaussian() * 0.02,
                    reference.b() + rng.nextGaussian() * 0.02);
            samples.add(ColorSamples.Sample.of(measured, reference));
        }

        Matrix3 ccm = CcmSolver.solve(samples);

        assertThat(ccm.determinant())
                .as("CCM fit tu toan bo patch co reference trung tinh phai suy bien (det=0) — "
                        + "day la ly do ColorPipelineService.tryCardCcm phai kiem dinh thuc, khong chi residual")
                .isCloseTo(0.0, within(1e-9));
    }

    @Test
    @DisplayName("applyCardCcm voi ma tran KHONG suy bien: Lab dau ra khop mau sRGB da biet, method=CARD_CCM, residual giu nguyen")
    void applyCardCcm_withWellConditionedMatrix_producesExpectedLab() throws IOException {
        String hex = "#5AA23C";
        BufferedImage flat = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        int rgb = java.awt.Color.decode(hex).getRGB() & 0xFFFFFF;
        fillRect(flat, 0, 0, 64, 64, rgb);

        RawImagePort rawImagePort = new OpenCvRawImageProcessor(nativeLoader);
        VisionEngine.FrameHandle frame = rawImagePort.decode(toPngBytes(flat)).frame();

        RawImagePort.CalibrationResult result = rawImagePort.applyCardCcm(
                frame, LinearizationCurve.standard(), Matrix3.IDENTITY, 0.321);

        assertThat(result.method()).isEqualTo(CalibrationMethod.CARD_CCM);
        assertThat(result.calibrationResidualDe00Proxy()).isEqualTo(0.321);
        assertThat(result.wbGains()).containsExactly(1.0, 1.0, 1.0);

        Lab expected = ColorSpace.hexToLab(hex);
        Lab actual = ColorSpace.xyzToLab(result.xyzFrame().at(10, 10));
        assertThat(actual.l()).isCloseTo(expected.l(), within(0.5));
        assertThat(actual.a()).isCloseTo(expected.a(), within(0.5));
        assertThat(actual.b()).isCloseTo(expected.b(), within(0.5));
    }


    private PipelineOutcome runPipeline(CardLayout layout, byte[] cardBytes, byte[] sandBytes,
                                         CardLayoutCatalog cardLayoutCatalog) throws IOException {
        RawImagePort rawImagePort = new OpenCvRawImageProcessor(nativeLoader);
        VisionEngine.FrameHandle canonicalCard = rawImagePort.decode(cardBytes).frame();
        ColorPipelineService pipeline = buildPipeline(rawImagePort,
                new FoundCardVisionEngine(new OpenCvVisionEngine(nativeLoader), canonicalCard),
                sampleChartCatalog(), cardLayoutCatalog);
        return pipeline.analyze(sandBytes, RawImagePort.RoiRect.fullFrame(), VisionEngine.QuadHint.empty());
    }

    private static ColorPipelineService buildPipeline(RawImagePort rawImagePort, VisionEngine visionEngine,
                                                        ChartCatalog chartCatalog,
                                                        CardLayoutCatalog cardLayoutCatalog) {
        var substrateProfileCatalog = (com.catcheck.scan.domain.color.SubstrateProfileCatalog)
                (line, batch) -> Optional.of(new Lab(66.814, 3.154, 10.461));
        return new ColorPipelineService(rawImagePort, visionEngine, chartCatalog, cardLayoutCatalog,
                substrateProfileCatalog, Clock.systemUTC());
    }

    private static void invokePostConstruct(OpenCvNativeLoader loader) {
        try {
            Method load = OpenCvNativeLoader.class.getDeclaredMethod("load");
            load.setAccessible(true);
            load.invoke(loader);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Khong goi duoc OpenCvNativeLoader.load() qua reflection", e);
        }
    }

    private record FoundCardVisionEngine(VisionEngine real, VisionEngine.FrameHandle canonicalCard)
            implements VisionEngine {

        @Override
        public CardDetectionResult detectCard(FrameHandle image, QuadHint hint) {
            return new CardDetectionResult(QuadHint.empty(), canonicalCard, CardDetectionResult.Method.ARUCO);
        }

        @Override
        public List<Blob> selectIndicatorGrains(XyzFrame xyzImage, Lab substrateLab, GrainThresholds thresholds) {
            return real.selectIndicatorGrains(xyzImage, substrateLab, thresholds);
        }
    }


    private static CardLayout buildCardLayout() {
        List<CardLayout.Patch> patches = List.of(
                neutral("W1", 0.06, 0.12, 0.14, 0.16, 95.494),
                neutral("G80", 0.22, 0.12, 0.14, 0.16, 82.046),
                neutral("G50", 0.38, 0.12, 0.14, 0.16, 53.585),
                neutral("G20", 0.54, 0.12, 0.14, 0.16, 21.247),
                neutral("BK", 0.70, 0.12, 0.14, 0.16, 5.063));
        return new CardLayout("CC-CARD", 1, CARD_WIDTH, CARD_HEIGHT, patches);
    }

    private static CardLayout.Patch neutral(String id, double x, double y, double w, double h, double labL) {
        return new CardLayout.Patch(id, x, y, w, h, CardLayout.Role.NEUTRAL, new Lab(labL, 0.0, 0.0));
    }


    private static byte[] buildCardImageBytes(CardLayout layout, boolean applyOffset) throws IOException {
        BufferedImage image = new BufferedImage(CARD_WIDTH, CARD_HEIGHT, BufferedImage.TYPE_INT_RGB);
        fillRect(image, 0, 0, CARD_WIDTH, CARD_HEIGHT, 0xFFFFFF);

        List<CardLayout.Patch> neutralPatches = layout.neutralPatches();
        for (int i = 0; i < neutralPatches.size(); i++) {
            CardLayout.Patch patch = neutralPatches.get(i);
            int x0 = (int) Math.round(patch.x() * CARD_WIDTH);
            int y0 = (int) Math.round(patch.y() * CARD_HEIGHT);
            int pw = (int) Math.round(patch.w() * CARD_WIDTH);
            int ph = (int) Math.round(patch.h() * CARD_HEIGHT);

            fillRect(image, x0, y0, pw, ph, 0xFF00FF);

            int trueEncoded = trueEncodedChannel(patch.refLab());
            int r = trueEncoded;
            int g = trueEncoded;
            int b = trueEncoded;
            if (applyOffset) {
                r = clamp8(trueEncoded + MEASURED_OFFSET[i][0]);
                g = clamp8(trueEncoded + MEASURED_OFFSET[i][1]);
                b = clamp8(trueEncoded + MEASURED_OFFSET[i][2]);
            }
            int insetX = (int) Math.round(pw * CLEAN_INSET_FRAC);
            int insetY = (int) Math.round(ph * CLEAN_INSET_FRAC);
            fillRect(image, x0 + insetX, y0 + insetY, pw - 2 * insetX, ph - 2 * insetY,
                    (r << 16) | (g << 8) | b);
        }
        return toPngBytes(image);
    }

    private static int trueEncodedChannel(Lab refLab) {
        RgbLinear linear = ColorSpace.labToLinearRgb(refLab);
        double meanLinear = (linear.r() + linear.g() + linear.b()) / 3.0;
        return ColorSpace.linearToSrgb8(meanLinear);
    }

    private static void fillRect(BufferedImage image, int x, int y, int w, int h, int rgb) {
        int xEnd = Math.min(image.getWidth(), x + Math.max(0, w));
        int yEnd = Math.min(image.getHeight(), y + Math.max(0, h));
        for (int yy = Math.max(0, y); yy < yEnd; yy++) {
            for (int xx = Math.max(0, x); xx < xEnd; xx++) {
                image.setRGB(xx, yy, rgb);
            }
        }
    }

    private static int clamp8(int v) {
        return Math.max(0, Math.min(255, v));
    }


    private static byte[] buildSandImageBytes() throws IOException {
        Random rng = new Random(20261009L);
        BufferedImage image = new BufferedImage(SAND_CANVAS, SAND_CANVAS, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < SAND_CANVAS; y++) {
            for (int x = 0; x < SAND_CANVAS; x++) {
                int v = clamp8(BACKGROUND_GRAY + rng.nextInt(2 * NOISE_AMPLITUDE + 1) - NOISE_AMPLITUDE);
                image.setRGB(x, y, (v << 16) | (v << 8) | v);
            }
        }
        int grainRgb = java.awt.Color.decode(GRAIN_HEX).getRGB() & 0xFFFFFF;
        for (int i = 0; i < GRAINS_PER_IMAGE; i++) {
            int cx = GRAIN_DIAMETER + rng.nextInt(SAND_CANVAS - 2 * GRAIN_DIAMETER);
            int cy = GRAIN_DIAMETER + rng.nextInt(SAND_CANVAS - 2 * GRAIN_DIAMETER);
            fillOval(image, cx, cy, GRAIN_DIAMETER, grainRgb);
        }
        return toPngBytes(image);
    }

    private static void fillOval(BufferedImage image, int cx, int cy, int diameter, int rgb) {
        int radius = diameter / 2;
        for (int y = -radius; y <= radius; y++) {
            for (int x = -radius; x <= radius; x++) {
                if (x * x + y * y <= radius * radius) {
                    int px = cx + x;
                    int py = cy + y;
                    if (px >= 0 && px < image.getWidth() && py >= 0 && py < image.getHeight()) {
                        image.setRGB(px, py, rgb);
                    }
                }
            }
        }
    }

    private static byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new UncheckedIOException(new IOException("Khong tim thay ImageIO writer cho png"));
        }
        return out.toByteArray();
    }


    private static ChartCatalog sampleChartCatalog() {
        PhChart chart = PhChart.of("chart-test", List.of(
                point("p1", 5.5, "#8FB6C9"),
                point("p2", 6.3, "#C9CE8F"),
                point("p3", 6.6, GRAIN_HEX),
                point("p4", 7.5, "#C98F8F")));
        List<PhBandClassifier.Band> bands = List.of(
                new PhBandClassifier.Band(PhBandClassifier.Code.LOW, null, 5.9, true, true,
                        "ph.low", PhBandClassifier.Severity.WATCH, "color-ph-abnormal", "alert-triangle", 1),
                new PhBandClassifier.Band(PhBandClassifier.Code.SLIGHTLY_LOW, 6.0, 6.29, true, true,
                        "ph.slightlyLow", PhBandClassifier.Severity.ATTENTION, "color-ph-mild", "arrow-down-circle", 2),
                new PhBandClassifier.Band(PhBandClassifier.Code.IN_RANGE, 6.3, 6.6, true, true,
                        "ph.inRange", PhBandClassifier.Severity.NORMAL, "color-ph-normal", "check-circle", 3),
                new PhBandClassifier.Band(PhBandClassifier.Code.SLIGHTLY_HIGH, 6.61, 7.0, true, true,
                        "ph.slightlyHigh", PhBandClassifier.Severity.ATTENTION, "color-ph-mild", "arrow-up-circle", 4),
                new PhBandClassifier.Band(PhBandClassifier.Code.HIGH, 7.1, null, true, true,
                        "ph.high", PhBandClassifier.Severity.WATCH, "color-ph-abnormal", "alert-triangle", 5),
                new PhBandClassifier.Band(PhBandClassifier.Code.INCONCLUSIVE, null, null, true, true,
                        "ph.inconclusive", PhBandClassifier.Severity.NEUTRAL, "color-ph-unknown", "help-circle", 6));

        return new ChartCatalog() {
            @Override
            public Optional<PhChart> findActiveChart(String productLine, String productionBatch) {
                return Optional.of(chart);
            }

            @Override
            public Optional<PhChart> findChartById(UUID chartId) {
                return Optional.of(chart);
            }

            @Override
            public List<PhBandClassifier.Band> findGlobalBands() {
                return bands;
            }
        };
    }

    private static PhChartPoint point(String id, double ph, String hex) {
        return new PhChartPoint(id, ph, ColorSpace.hexToLab(hex), 4.0, hex, hex, id, id, 5, 1.0);
    }
}
