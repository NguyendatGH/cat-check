package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import com.catcheck.scan.domain.QualityFlagCode;
import com.catcheck.scan.domain.QualityFlagSeverity;
import com.catcheck.scan.domain.ScanClassification;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * docs/scan-production-plan.md GĐ7 — metrics quanh lệnh gọi {@code ColorPipelineService.analyze}.
 * Dùng {@link SimpleMeterRegistry} (Micrometer test fixture, không cần Actuator/DB thật) để xác
 * nhận tên metric + tag đúng như báo cáo, và rằng không có tag cardinality cao/PII nào bị gắn.
 */
class ScanPipelineMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ScanPipelineMetrics metrics = new ScanPipelineMetrics(registry);

    @Test
    void analyzedOutcomeIncrementsOutcomeAndCalibrationAndConfidence() {
        PipelineOutcome outcome = outcome(false, CalibrationMethod.SUBSTRATE_WB,
                new BigDecimal("0.800"), List.of(new QualityFlag(QualityFlagCode.LOW_GRANULE_COVERAGE, QualityFlagSeverity.WARN)));

        PipelineOutcome returned = metrics.recordPipelineCall(() -> outcome);

        assertEquals(outcome, returned, "Phai tra dung outcome goc, khong boc/sua gi them");
        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_OUTCOME)
                .tag("result", "ANALYZED").counter().count());
        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_CALIBRATION_METHOD)
                .tag("method", "SUBSTRATE_WB").counter().count());
        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_QUALITY_FLAG)
                .tag("code", "LOW_GRANULE_COVERAGE").counter().count());
        assertEquals(1, registry.get(ScanPipelineMetrics.SUMMARY_CONFIDENCE).summary().count());
        assertEquals(0.8, registry.get(ScanPipelineMetrics.SUMMARY_CONFIDENCE).summary().totalAmount(), 1e-9);
        assertEquals(1, registry.get(ScanPipelineMetrics.TIMER_DURATION).timer().count());
    }

    @Test
    void inconclusiveOutcomeIsCountedSeparatelyFromAnalyzed() {
        PipelineOutcome outcome = outcome(true, CalibrationMethod.NONE,
                new BigDecimal("0.200"), List.of());

        metrics.recordPipelineCall(() -> outcome);

        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_OUTCOME)
                .tag("result", "INCONCLUSIVE").counter().count());
        assertThrows(Exception.class, () -> registry.get(ScanPipelineMetrics.COUNTER_OUTCOME)
                .tag("result", "ANALYZED").counter(),
                "Khong duoc tao counter ANALYZED khi chua xay ra lan nao");
    }

    @Test
    void multipleQualityFlagsAreEachCountedOnce() {
        PipelineOutcome outcome = outcome(false, CalibrationMethod.CARD_CCM,
                new BigDecimal("0.900"), List.of(
                        new QualityFlag(QualityFlagCode.UNEVEN_LIGHTING, QualityFlagSeverity.WARN),
                        new QualityFlag(QualityFlagCode.FEW_BLOBS, QualityFlagSeverity.WARN)));

        metrics.recordPipelineCall(() -> outcome);

        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_QUALITY_FLAG)
                .tag("code", "UNEVEN_LIGHTING").counter().count());
        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_QUALITY_FLAG)
                .tag("code", "FEW_BLOBS").counter().count());
        assertEquals(1.0, registry.get(ScanPipelineMetrics.COUNTER_CALIBRATION_METHOD)
                .tag("method", "CARD_CCM").counter().count());
    }

    @Test
    void timerStillRecordsWhenPipelineCallThrows_andOutcomeCountersAreUntouched() {
        RuntimeException boom = new RuntimeException("loi vision engine gia lap");

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> metrics.recordPipelineCall(() -> {
            throw boom;
        }));

        assertEquals(boom, thrown, "Ngoai le goc phai duoc nem lai nguyen ven, khong boc lai");
        assertEquals(1, registry.get(ScanPipelineMetrics.TIMER_DURATION).timer().count(),
                "Thoi gian van phai duoc ghi nhan du loi (khong bi nuot)");
        assertEquals(0, registry.find(ScanPipelineMetrics.COUNTER_OUTCOME).counters().size(),
                "Khong co PipelineOutcome nen khong duoc tao bat ky counter outcome nao");
        assertEquals(0, registry.find(ScanPipelineMetrics.COUNTER_CALIBRATION_METHOD).counters().size());
        assertEquals(0, registry.find(ScanPipelineMetrics.COUNTER_QUALITY_FLAG).counters().size());
        assertEquals(0, registry.find(ScanPipelineMetrics.SUMMARY_CONFIDENCE).summaries().size());
    }

    private PipelineOutcome outcome(boolean inconclusive, CalibrationMethod method,
                                     BigDecimal confidence, List<QualityFlag> flags) {
        return new PipelineOutcome(
                inconclusive,
                inconclusive ? ScanClassification.INCONCLUSIVE : ScanClassification.IN_RANGE,
                null, null, null,
                confidence,
                "MEDIUM",
                false,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null,
                method,
                null,
                Map.of(),
                Map.of(),
                flags,
                null, null, false,
                "test-1.0.0",
                0,
                null,
                0, 0, null);
    }
}
