package com.catcheck.ai.infrastructure.provider;

import com.catcheck.ai.infrastructure.config.AiProperties;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiCompatibleClientTest {

    private static final List<Map<String, String>> PROMPT = List.of(Map.of("role", "user", "content", "hi"));

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void providerFailureFallsBackToEmptyInsteadOfThrowing() {
        // Cổng 1 trên loopback: kết nối bị từ chối ngay ⇒ ResourceAccessException trong client.
        AiProperties properties = new AiProperties(true, "http://127.0.0.1:1", "test-key", "model", 6, 8, 100,
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThat(client(properties).complete(PROMPT)).isEmpty();
    }

    @Test
    void missingTimeoutsAndBudgetsDefaultToFiniteValues() {
        AiProperties properties = new AiProperties(false, "http://localhost", "", "model", 6, 8, 100, null, Duration.ZERO);

        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.maxConcurrentCalls()).isEqualTo(2);
        assertThat(properties.circuitOpenFor()).isEqualTo(Duration.ofSeconds(60));
    }

    @Test
    void successfulAnswerIsReturnedAndTokenUsageIsMetered() throws IOException {
        AtomicInteger hits = serve(200, Map.of(), """
                {"choices":[{"message":{"content":" Trả lời [1] "}}],"usage":{"total_tokens":42}}""");

        assertThat(client(properties()).complete(PROMPT)).contains("Trả lời [1]");
        assertThat(hits).hasValue(1);
        assertThat(meters.counter("catcheck.ai.provider.tokens", "model", "model").count()).isEqualTo(42);
    }

    @Test
    void rateLimitOpensTheCircuitSoTheNextQuestionDoesNotCallTheProvider() throws IOException {
        AtomicInteger hits = serve(429, Map.of("Retry-After", "120"), "{}");
        OpenAiCompatibleClient client = client(properties());

        assertThat(client.complete(PROMPT)).isEmpty();
        assertThat(client.complete(PROMPT)).isEmpty();

        assertThat(hits).hasValue(1);
        assertThat(meters.counter("catcheck.ai.provider.skipped", "reason", "circuit-open").count()).isEqualTo(1);
    }

    private AiProperties properties() {
        return new AiProperties(true, "http://127.0.0.1:" + server.getAddress().getPort(), "test-key", "model",
                6, 8, 100, Duration.ofSeconds(2), Duration.ofSeconds(2), null, 0, 1, null);
    }

    private OpenAiCompatibleClient client(AiProperties properties) {
        return new OpenAiCompatibleClient(properties, meters, Clock.systemUTC());
    }

    private AtomicInteger serve(int status, Map<String, String> headers, String body) throws IOException {
        AtomicInteger hits = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/chat/completions", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return hits;
    }
}
