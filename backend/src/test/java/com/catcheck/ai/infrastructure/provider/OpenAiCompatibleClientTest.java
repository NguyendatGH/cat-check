package com.catcheck.ai.infrastructure.provider;

import com.catcheck.ai.infrastructure.config.AiProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleClientTest {

    @Test
    void providerFailureFallsBackToEmptyInsteadOfThrowing() {
        // Cổng 1 trên loopback: kết nối bị từ chối ngay ⇒ ResourceAccessException trong client.
        AiProperties properties = new AiProperties(true, "http://127.0.0.1:1", "test-key", "model", 6, 8, 100,
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThat(new OpenAiCompatibleClient(properties).complete(List.of(Map.of("role", "user", "content", "hi"))))
                .isEmpty();
    }

    @Test
    void missingTimeoutsDefaultToFiniteValues() {
        AiProperties properties = new AiProperties(false, "http://localhost", "", "model", 6, 8, 100, null, Duration.ZERO);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
    }
}
