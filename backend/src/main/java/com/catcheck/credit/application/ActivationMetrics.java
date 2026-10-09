package com.catcheck.credit.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Đo lường vận hành quanh {@link ActivateCreditCodeService#activate} (G9,
 * {@code docs/cat-rag-credit-pipeline.md} B2 — "chưa có metric kích hoạt/tiêu/hết hạn"), cùng
 * kiểu với {@code scan.application.ScanPipelineMetrics}.
 *
 * <p>Cố ý KHÔNG nằm trong {@code ActivateCreditCodeService}: tách riêng để test độc lập bằng
 * {@code io.micrometer.core.instrument.simple.SimpleMeterRegistry} mà không cần dựng toàn bộ
 * service (cùng lý do {@code ScanPipelineMetrics} đã ghi).</p>
 *
 * <p><b>Không có tag cardinality cao hoặc PII</b> — không mã kích hoạt (thô hay băm), không
 * {@code userId}, không IP. Tag {@code result} chỉ nhận một trong sáu giá trị cố định:
 * {@code SUCCESS} hoặc tên một {@link com.catcheck.credit.api.CreditErrorCode} liên quan tới
 * kích hoạt — cả hai loại đều là tập hợp cố định, không phải giá trị do người dùng tự nhập.</p>
 */
@Component
public class ActivationMetrics {

    /** Đếm kết quả mỗi lần gọi {@code POST /activations}, theo {@code result}. */
    static final String COUNTER_ACTIVATION_RESULT = "catcheck.credit.activation.result";

    /** Giá trị tag {@code result} khi kích hoạt thành công. */
    static final String RESULT_SUCCESS = "SUCCESS";

    private final MeterRegistry meterRegistry;

    public ActivationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /** Ghi nhận một lần kích hoạt thành công. */
    public void recordSuccess() {
        increment(RESULT_SUCCESS);
    }

    /**
     * Ghi nhận một lần kích hoạt thất bại.
     *
     * @param errorCode tên hằng lỗi (vd {@code ACTIVATION_CODE_MALFORMED},
     *                  {@code ACTIVATION_RATE_LIMITED}) — KHÔNG phải mã kích hoạt của người dùng.
     */
    public void recordFailure(String errorCode) {
        increment(errorCode);
    }

    private void increment(String result) {
        Counter.builder(COUNTER_ACTIVATION_RESULT)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
