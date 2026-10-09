package com.catcheck.credit.application;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * G9 ({@code docs/cat-rag-credit-pipeline.md} B2) — đếm kết quả {@code POST /activations}, cùng
 * kiểu test với {@code scan.application.ScanPipelineMetricsTest}: dùng {@link SimpleMeterRegistry}
 * (không cần Actuator/DB thật) để xác nhận tên metric + tag.
 */
class ActivationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final ActivationMetrics metrics = new ActivationMetrics(registry);

    @Test
    void recordSuccessIncrementsOnlyTheSuccessTag() {
        metrics.recordSuccess();

        assertEquals(1.0, registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", ActivationMetrics.RESULT_SUCCESS).counter().count());
        assertThrows(Exception.class, () -> registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", "ACTIVATION_CODE_INVALID").counter(),
                "Khong duoc tao counter cho ma loi chua xay ra lan nao");
    }

    @Test
    void recordFailureTagsByErrorCodeNameNotByRawCodeOrUserId() {
        metrics.recordFailure("ACTIVATION_CODE_MALFORMED");
        metrics.recordFailure("ACTIVATION_CODE_MALFORMED");
        metrics.recordFailure("ACTIVATION_RATE_LIMITED");

        assertEquals(2.0, registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", "ACTIVATION_CODE_MALFORMED").counter().count());
        assertEquals(1.0, registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", "ACTIVATION_RATE_LIMITED").counter().count());
    }

    @Test
    void successAndFailureAreIndependentCounters() {
        metrics.recordSuccess();
        metrics.recordSuccess();
        metrics.recordFailure("ACTIVATION_CODE_EXPIRED");

        assertEquals(2.0, registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", ActivationMetrics.RESULT_SUCCESS).counter().count());
        assertEquals(1.0, registry.get(ActivationMetrics.COUNTER_ACTIVATION_RESULT)
                .tag("result", "ACTIVATION_CODE_EXPIRED").counter().count());
    }
}
