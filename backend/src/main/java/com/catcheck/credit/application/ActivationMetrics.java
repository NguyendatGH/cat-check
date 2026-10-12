package com.catcheck.credit.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class ActivationMetrics {

    static final String COUNTER_ACTIVATION_RESULT = "catcheck.credit.activation.result";

    static final String RESULT_SUCCESS = "SUCCESS";

    private final MeterRegistry meterRegistry;

    public ActivationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordSuccess() {
        increment(RESULT_SUCCESS);
    }

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
