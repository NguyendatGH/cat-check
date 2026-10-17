package com.catcheck.ai.infrastructure.health;

import com.catcheck.ai.application.AiEmbeddingPort;
import com.catcheck.ai.infrastructure.provider.OpenAiCompatibleClient;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Trạng thái trợ lý RAG cho vận hành (/actuator/health → "ai"). Luôn UP: trợ lý tự hạ về fallback có
 * trích dẫn khi thiếu embedding/provider, nên không được kéo readiness của cả app xuống.
 */
@Component("ai")
public class AiHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbc;
    private final AiEmbeddingPort embedding;
    private final OpenAiCompatibleClient provider;

    public AiHealthIndicator(JdbcTemplate jdbc, AiEmbeddingPort embedding, OpenAiCompatibleClient provider) {
        this.jdbc = jdbc;
        this.embedding = embedding;
        this.provider = provider;
    }

    @Override
    public Health health() {
        Integer documents = jdbc.queryForObject("SELECT count(*) FROM ai_document WHERE active = TRUE", Integer.class);
        Integer pending = jdbc.queryForObject("""
                SELECT count(*) FROM ai_chunk c JOIN ai_document d ON d.id = c.document_id
                WHERE d.active = TRUE AND (c.embedding IS NULL OR c.embedding_model IS DISTINCT FROM ?)
                """, Integer.class, embedding.modelId());
        return Health.up()
                .withDetail("indexedDocuments", documents)
                .withDetail("embedding", embedding.available() ? embedding.modelId() : "disabled")
                .withDetail("chunksMissingEmbedding", embedding.available() ? pending : null)
                .withDetail("provider", provider.enabled() ? "enabled" : "disabled")
                .withDetail("providerCircuitOpen", provider.circuitOpen())
                .build();
    }
}
