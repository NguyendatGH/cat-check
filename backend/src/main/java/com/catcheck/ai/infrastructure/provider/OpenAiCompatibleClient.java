package com.catcheck.ai.infrastructure.provider;

import com.catcheck.ai.application.AiProviderPort;
import com.catcheck.ai.infrastructure.config.AiProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** HTTP adapter for OpenAI-compatible chat-completions APIs. */
@Component
public class OpenAiCompatibleClient implements AiProviderPort {

    private final AiProperties properties;
    private final RestClient client;

    public OpenAiCompatibleClient(AiProperties properties) {
        this.properties = properties;
        this.client = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public Optional<String> complete(List<Map<String, String>> messages) {
        if (!properties.enabled() || properties.apiKey() == null || properties.apiKey().isBlank()) {
            return Optional.empty();
        }
        Map<String, Object> request = Map.of(
                "model", properties.chatModel(),
                "messages", messages,
                "temperature", 0.1,
                "max_tokens", properties.maxTokens());
        try {
            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .body(request)
                    .retrieve()
                    .body(Map.class);
            if (response == null || !(response.get("choices") instanceof List<?> choices) || choices.isEmpty()) {
                return Optional.empty();
            }
            Object first = choices.getFirst();
            if (!(first instanceof Map<?, ?> choice) || !(choice.get("message") instanceof Map<?, ?> message)) {
                return Optional.empty();
            }
            Object content = message.get("content");
            return content instanceof String text && !text.isBlank() ? Optional.of(text.strip()) : Optional.empty();
        } catch (RuntimeException ignored) {
            // A configured provider is an enhancement; the grounded local fallback still keeps
            // the chatbot usable during local development and provider outages.
            return Optional.empty();
        }
    }
}
