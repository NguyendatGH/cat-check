package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class ScanPipelineMetrics {

    static final String TIMER_DURATION = "catcheck.scan.pipeline.duration";

    static final String COUNTER_OUTCOME = "catcheck.scan.pipeline.outcome";

    static final String COUNTER_CALIBRATION_METHOD = "catcheck.scan.pipeline.calibration.method";

    static final String COUNTER_QUALITY_FLAG = "catcheck.scan.pipeline.quality.flag";

    static final String SUMMARY_CONFIDENCE = "catcheck.scan.pipeline.confidence";

    private final MeterRegistry meterRegistry;

    public ScanPipelineMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public PipelineOutcome recordPipelineCall(Supplier<PipelineOutcome> pipelineCall) {
        PipelineOutcome outcome = Timer.builder(TIMER_DURATION)
                .register(meterRegistry)
                .record(pipelineCall);
        recordOutcome(outcome);
        return outcome;
    }

    private void recordOutcome(PipelineOutcome outcome) {
        Counter.builder(COUNTER_OUTCOME)
                .tag("result", outcome.inconclusive() ? "INCONCLUSIVE" : "ANALYZED")
                .register(meterRegistry)
                .increment();

        CalibrationMethod method = outcome.calibrationMethod();
        Counter.builder(COUNTER_CALIBRATION_METHOD)
                .tag("method", (method == null ? CalibrationMethod.NONE : method).name())
                .register(meterRegistry)
                .increment();

        for (QualityFlag flag : outcome.qualityFlags()) {
            Counter.builder(COUNTER_QUALITY_FLAG)
                    .tag("code", flag.code().name())
                    .register(meterRegistry)
                    .increment();
        }

        if (outcome.confidence() != null) {
            DistributionSummary.builder(SUMMARY_CONFIDENCE)
                    .register(meterRegistry)
                    .record(outcome.confidence().doubleValue());
        }
    }
}
