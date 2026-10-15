package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.color.CardLayout;
import com.catcheck.scan.domain.color.ChartCatalog;
import com.catcheck.scan.domain.color.ColorSpace;
import com.catcheck.scan.domain.color.Lab;
import com.catcheck.scan.domain.color.LinearizationCurve;
import com.catcheck.scan.domain.color.Matrix3;
import com.catcheck.scan.domain.color.PhBandClassifier;
import com.catcheck.scan.domain.color.PhChart;
import com.catcheck.scan.domain.color.PhChartPoint;
import com.catcheck.scan.domain.color.Xyz;
import com.catcheck.scan.domain.color.port.VisionEngine;
import com.catcheck.scan.domain.port.RawImagePort;
import com.catcheck.shared.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ColorPipelineServiceFrameCloseTest {

    private final List<FakeFrame> created = new ArrayList<>();

    @Test
    void allNativeFramesAreClosedAfterAnalysisEndsEarly() {
        PipelineOutcome outcome = pipeline(1000, 200.0, List.of()).analyze(
                new byte[] {1}, RawImagePort.RoiRect.fullFrame(), VisionEngine.QuadHint.empty());

        assertThat(outcome.inconclusive()).isTrue();
        // decoded, roi, canonical card, xyz
        assertThat(created).hasSize(4).allMatch(FakeFrame::closedOnce);
    }

    @Test
    void framesAreClosedWhenQualityBlocksTheScan() {
        pipeline(1000, 10.0, List.of()).analyze(
                new byte[] {1}, RawImagePort.RoiRect.fullFrame(), VisionEngine.QuadHint.empty());

        assertThat(created).hasSize(3).allMatch(FakeFrame::closedOnce);
    }

    @Test
    void decodedFrameIsClosedWhenValidationThrows() {
        ColorPipelineService pipeline = pipeline(100, 200.0, List.of());

        assertThrows(BusinessRuleException.class, () -> pipeline.analyze(
                new byte[] {1}, RawImagePort.RoiRect.fullFrame(), VisionEngine.QuadHint.empty()));

        assertThat(created).hasSize(1).allMatch(FakeFrame::closedOnce);
    }

    private ColorPipelineService pipeline(int edge, double blur, List<VisionEngine.Blob> blobs) {
        RawImagePort raw = new FakeRawImagePort(edge, blur);
        VisionEngine vision = new VisionEngine() {
            @Override
            public CardDetectionResult detectCard(FrameHandle image, QuadHint hint) {
                return new CardDetectionResult(QuadHint.empty(), frame(), CardDetectionResult.Method.ARUCO);
            }

            @Override
            public List<Blob> selectIndicatorGrains(XyzFrame xyzImage, Lab substrateLab, GrainThresholds thresholds) {
                return blobs;
            }
        };
        PhChart chart = PhChart.of(UUID.randomUUID().toString(), List.of(
                point("p1", 6.0, "#8FB6C9"), point("p2", 7.0, "#C98F8F")));
        ChartCatalog charts = new ChartCatalog() {
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
                return List.of();
            }
        };
        return new ColorPipelineService(raw, vision, charts, code -> Optional.empty(),
                (line, batch) -> Optional.of(new Lab(66.8, 3.2, 10.5)), Clock.systemUTC());
    }

    private static PhChartPoint point(String id, double ph, String hex) {
        return new PhChartPoint(id, ph, ColorSpace.hexToLab(hex), 4.0, hex, hex, id, id, 5, 1.0);
    }

    private FakeFrame frame() {
        FakeFrame frame = new FakeFrame();
        created.add(frame);
        return frame;
    }

    private static final class FakeFrame implements VisionEngine.XyzFrame {

        private int closes;

        boolean closedOnce() {
            return closes == 1;
        }

        @Override
        public VisionEngine.FrameSize size() {
            return new VisionEngine.FrameSize(10, 10);
        }

        @Override
        public Xyz at(int x, int y) {
            return new Xyz(0.2, 0.2, 0.2);
        }

        @Override
        public int pixelCount() {
            return 100;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    private final class FakeRawImagePort implements RawImagePort {

        private final int edge;
        private final double blur;

        private FakeRawImagePort(int edge, double blur) {
            this.edge = edge;
            this.blur = blur;
        }

        @Override
        public DecodedImage decode(byte[] bytes) {
            return new DecodedImage(frame(), edge, edge, "image/png");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public QualityMetrics measureQuality(VisionEngine.FrameHandle frame) {
            return new QualityMetrics(blur, 120.0, 0.0, 0.0, 0.0);
        }

        @Override
        public VisionEngine.FrameHandle cropToRoi(VisionEngine.FrameHandle frame, RoiRect roi) {
            return frame();
        }

        @Override
        public CalibrationResult calibrate(VisionEngine.FrameHandle roiFrame, Lab referenceSubstrateLab) {
            return new CalibrationResult(frame(), CalibrationMethod.SUBSTRATE_WB, new double[] {1, 1, 1},
                    0.5, 1.0, referenceSubstrateLab);
        }

        @Override
        public List<CardPatchSample> sampleNeutralPatches(VisionEngine.FrameHandle canonicalCard,
                                                          CardLayout cardLayout) {
            return List.of();
        }

        @Override
        public CalibrationResult applyCardCcm(VisionEngine.FrameHandle roiFrame, LinearizationCurve curve,
                                              Matrix3 ccm, double residualDe00) {
            return new CalibrationResult(frame(), CalibrationMethod.CARD_CCM, new double[] {1, 1, 1},
                    1.0, residualDe00, new Lab(66.8, 3.2, 10.5));
        }
    }
}
