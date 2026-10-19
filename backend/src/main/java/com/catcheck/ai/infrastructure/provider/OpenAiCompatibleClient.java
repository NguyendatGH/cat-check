package com.catcheck.ai.infrastructure.provider;

import com.catcheck.ai.application.AiProviderPort;
import com.catcheck.ai.infrastructure.config.AiProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Client chat-completions tương thích OpenAI (OpenRouter). Tối đa một lần gọi cho mỗi lượt hỏi, không
 * retry: lỗi/429/timeout/đang quá tải đều trả rỗng để service dùng retrieval fallback có trích dẫn.
 */
@Component
public class OpenAiCompatibleClient implements AiProviderPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final AiProperties properties;
    private final RestClient client;
    private final MeterRegistry meters;
    private final Clock clock;
    // shortcut: semaphore + circuit chỉ trong một JVM; nhiều replica thì quota thật của provider cần
    // bộ đếm dùng chung (vd. Postgres/Redis) — xem docs/AI_HYBRID_RAG_PLAN.md mục 5.
    private final Semaphore inFlight;
    private final AtomicReference<Instant> circuitOpenUntil = new AtomicReference<>(Instant.MIN);

    public OpenAiCompatibleClient(AiProperties properties, MeterRegistry meters, Clock clock) {
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        this.inFlight = new Semaphore(properties.maxConcurrentCalls());
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public Optional<String> complete(List<Map<String, String>> messages) {
        if (!enabled()) {
            return Optional.empty();
        }
        if (circuitOpen()) {
            count("circuit-open");
            return Optional.empty();
        }
        if (!inFlight.tryAcquire()) {
            count("busy");
            return Optional.empty();
        }
        Timer.Sample sample = Timer.start(meters);
        String outcome = "error";
        try {
            Map<String, Object> request = new HashMap<>(Map.of(
                    "model", properties.chatModel(),
                    "messages", messages,
                    "temperature", 0.1,
                    "max_tokens", properties.maxTokens()));
            if (properties.baseUrl() != null && properties.baseUrl().contains("openrouter.ai")) {
                // Model free của OpenRouter mặc định "suy luận" trước: có thể tiêu hết max_tokens và trả content rỗng.
                request.put("reasoning", Map.of("enabled", false));
            }
            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            Optional<String> answer = answerOf(response);
            recordTokens(response);
            outcome = answer.isPresent() ? "ok" : "empty";
            if (answer.isEmpty()) {
                // Chỉ metadata, không nội dung: lý do dừng và model thật sự được định tuyến tới.
                log.warn("AI provider returned no answer text (finish_reason={}, model={}); using retrieval fallback",
                        finishReason(response), response == null ? null : response.get("model"));
            }
            return answer;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            outcome = "http-" + status;
            if (status == 429 || status >= 500) {
                openCircuit(status == 429 ? retryAfter(ex) : properties.circuitOpenFor());
            }
            log.warn("AI provider call failed (status={}); using retrieval fallback", status);
            return Optional.empty();
        } catch (RuntimeException ex) {
            // Chỉ log loại lỗi — KHÔNG log message của exception (có thể lặp lại prompt/response).
            outcome = "exception";
            openCircuit(properties.circuitOpenFor());
            log.warn("AI provider call failed ({}); using retrieval fallback", ex.getClass().getSimpleName());
            return Optional.empty();
        } finally {
            inFlight.release();
            sample.stop(meters.timer("catcheck.ai.provider.calls", "model", properties.chatModel(), "outcome", outcome));
        }
    }

    public boolean enabled() {
        return properties.enabled() && properties.apiKey() != null && !properties.apiKey().isBlank();
    }

    public boolean circuitOpen() {
        return clock.instant().isBefore(circuitOpenUntil.get());
    }

    private void openCircuit(Duration duration) {
        circuitOpenUntil.set(clock.instant().plus(duration));
    }

    private Duration retryAfter(RestClientResponseException ex) {
        String header = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (header != null) {
            try {
                long seconds = Long.parseLong(header.strip());
                if (seconds > 0) {
                    return Duration.ofSeconds(Math.min(seconds, 3600));
                }
            } catch (NumberFormatException ignored) {
                // Dạng HTTP-date hiếm gặp: dùng thời gian mặc định.
            }
        }
        return properties.circuitOpenFor();
    }

    private void count(String outcome) {
        meters.counter("catcheck.ai.provider.skipped", "reason", outcome).increment();
    }

    private void recordTokens(Map<?, ?> response) {
        if (response != null && response.get("usage") instanceof Map<?, ?> usage
                && usage.get("total_tokens") instanceof Number tokens) {
            meters.counter("catcheck.ai.provider.tokens", "model", properties.chatModel()).increment(tokens.doubleValue());
        }
    }

    private static Object finishReason(Map<?, ?> response) {
        if (response != null && response.get("choices") instanceof List<?> choices && !choices.isEmpty()
                && choices.getFirst() instanceof Map<?, ?> choice) {
            return choice.get("finish_reason");
        }
        return null;
    }

    private static Optional<String> answerOf(Map<?, ?> response) {
        if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
            return Optional.empty();
        }
        Object first = choices.getFirst();
        if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
            return Optional.empty();
        }
        Object content = message.get("content");
        return content instanceof String text && !text.isBlank() ? Optional.of(text.strip()) : Optional.empty();
    }
}
