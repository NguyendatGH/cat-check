package com.catcheck.scan.application;

import com.catcheck.scan.domain.CalibrationMethod;
import com.catcheck.scan.domain.QualityFlag;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Đo lường vận hành quanh LỆNH GỌI {@link ColorPipelineService#analyze} (docs/scan-production-plan.md
 * GĐ7 — "Metrics (Micrometer, đã có Actuator)").
 *
 * <p>Cố ý KHÔNG nằm trong {@code ColorPipelineService}/{@code scan.infrastructure.vision}/
 * {@code scan.domain.color} — các gói đó đang được một nhánh khác sửa song song để thêm
 * {@code CARD_CCM}. Class này bọc đo lường ở tầng gọi ({@link SubmitScanService}) nên không đụng
 * file nào trong các gói trên, và tách riêng để test được độc lập bằng
 * {@code io.micrometer.core.instrument.simple.SimpleMeterRegistry} mà không cần dựng toàn bộ
 * {@code SubmitScanService}.</p>
 *
 * <p><b>Không có tag cardinality cao hoặc PII</b> — không {@code scanId}/{@code userId}/
 * {@code catId}/tên file. Mọi tag đều là giá trị enum/cờ cố định (kết quả, phương pháp hiệu
 * chỉnh, mã cờ chất lượng).</p>
 */
@Component
public class ScanPipelineMetrics {

    /** Thời gian chạy {@code ColorPipelineService.analyze(...)} — p7 GĐ7: cảnh báo khi p95 > 5s. */
    static final String TIMER_DURATION = "catcheck.scan.pipeline.duration";

    /** Tỉ lệ {@code ANALYZED} vs {@code INCONCLUSIVE} — p7 GĐ7: cảnh báo khi INCONCLUSIVE > 30%/giờ. */
    static final String COUNTER_OUTCOME = "catcheck.scan.pipeline.outcome";

    /** Phân bố tier hiệu chỉnh đã dùng (CARD_CCM/SUBSTRATE_WB/NONE). */
    static final String COUNTER_CALIBRATION_METHOD = "catcheck.scan.pipeline.calibration.method";

    /** Phân bố từng mã cờ chất lượng xuất hiện trong kết quả (p6 §6.3.4 Q1-Q5, S3-S9). */
    static final String COUNTER_QUALITY_FLAG = "catcheck.scan.pipeline.quality.flag";

    /** Phân bố {@code confidence} của các lượt phân tích ra được kết quả (không null). */
    static final String SUMMARY_CONFIDENCE = "catcheck.scan.pipeline.confidence";

    private final MeterRegistry meterRegistry;

    public ScanPipelineMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Bọc một lệnh gọi pipeline: đo thời gian xử lý (kể cả khi lệnh gọi ném ngoại lệ — thời gian
     * vẫn được ghi nhận rồi ngoại lệ mới được ném lại, không bị nuốt), sau đó — chỉ khi lệnh gọi
     * trả về bình thường — ghi nhận các bộ đếm theo kết quả.
     */
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
