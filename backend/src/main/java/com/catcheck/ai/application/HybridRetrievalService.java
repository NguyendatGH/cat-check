package com.catcheck.ai.application;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hybrid retrieval không gọi LLM: một round trip DB lấy ứng viên lexical + vector, hợp nhất bằng
 * Reciprocal Rank Fusion, lọc ngưỡng vector, tối đa {@link #MAX_CHUNKS_PER_DOCUMENT} chunk mỗi tài liệu.
 * Cache hit không chạm DB; publish/archive làm tăng {@code corpusVersion} nên cache cũ tự hết hiệu lực.
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private static final int RRF_K = 60;
    static final int CANDIDATES_PER_BRANCH = 20;
    static final int MAX_CHUNKS_PER_DOCUMENT = 2;

    private final AiKnowledgePort knowledge;
    private final AiEmbeddingPort embedding;
    private final AiRuntimeConfig config;
    // shortcut: version + cache sống trong một JVM; chạy nhiều replica thì publish ở replica khác chỉ thấy
    // sau TTL — khi scale out, lưu corpusVersion ở kho dùng chung (vd. app_setting) và đọc theo lô.
    private final AtomicLong corpusVersion = new AtomicLong();
    private final Cache<String, List<AiKnowledgePort.RetrievedChunk>> cache = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    public HybridRetrievalService(AiKnowledgePort knowledge, AiEmbeddingPort embedding, AiRuntimeConfig config) {
        this.knowledge = knowledge;
        this.embedding = embedding;
        this.config = config;
    }

    public List<AiKnowledgePort.RetrievedChunk> retrieve(String locale, String query, int limit) {
        List<String> terms = AiText.contentTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        String key = locale + '|' + corpusVersion.get() + '|' + limit + '|' + String.join(" ", terms);
        return cache.get(key, ignored -> search(locale, query, terms, limit));
    }

    /** Gọi sau mỗi lần index thay đổi kho tri thức. */
    public void corpusChanged() {
        corpusVersion.incrementAndGet();
    }

    private List<AiKnowledgePort.RetrievedChunk> search(String locale, String query, List<String> terms, int limit) {
        float[] queryVector = null;
        if (embedding.available()) {
            try {
                queryVector = embedding.embedQuery(query);
            } catch (RuntimeException ex) {
                log.warn("Query embedding failed ({}); lexical retrieval only", ex.getClass().getSimpleName());
            }
        }
        AiKnowledgePort.CandidateSet candidates = knowledge.searchCandidates(
                locale, terms, queryVector, embedding.modelId(), CANDIDATES_PER_BRANCH);
        List<AiKnowledgePort.RetrievedChunk> vector = candidates.vector().stream()
                .filter(chunk -> chunk.score() >= config.minVectorSimilarity())
                .toList();
        return capPerDocument(fuse(candidates.lexical(), vector), limit);
    }

    static List<AiKnowledgePort.RetrievedChunk> fuse(List<AiKnowledgePort.RetrievedChunk> lexical,
                                                     List<AiKnowledgePort.RetrievedChunk> vector) {
        if (vector.isEmpty()) {
            return lexical;
        }
        Map<String, RankedChunk> combined = new HashMap<>();
        addCandidates(combined, lexical);
        addCandidates(combined, vector);
        return combined.values().stream()
                .sorted(Comparator.comparingDouble(RankedChunk::score).reversed()
                        .thenComparing(item -> item.chunk().documentId())
                        .thenComparing(item -> item.chunk().content()))
                .map(RankedChunk::chunk)
                .toList();
    }

    private static List<AiKnowledgePort.RetrievedChunk> capPerDocument(List<AiKnowledgePort.RetrievedChunk> ranked,
                                                                       int limit) {
        Map<UUID, Integer> perDocument = new LinkedHashMap<>();
        List<AiKnowledgePort.RetrievedChunk> selected = new ArrayList<>();
        for (AiKnowledgePort.RetrievedChunk chunk : ranked) {
            if (selected.size() >= limit) {
                break;
            }
            if (perDocument.merge(chunk.documentId(), 1, Integer::sum) <= MAX_CHUNKS_PER_DOCUMENT) {
                selected.add(chunk);
            }
        }
        return List.copyOf(selected);
    }

    private static void addCandidates(Map<String, RankedChunk> combined,
                                      List<AiKnowledgePort.RetrievedChunk> candidates) {
        for (int i = 0; i < candidates.size(); i++) {
            AiKnowledgePort.RetrievedChunk chunk = candidates.get(i);
            String key = chunk.chunkId() != null ? chunk.chunkId().toString() : chunk.documentId() + "\n" + chunk.content();
            double contribution = 1.0 / (RRF_K + i + 1);
            combined.compute(key, (ignored, previous) -> previous == null
                    ? new RankedChunk(chunk, contribution)
                    : new RankedChunk(previous.chunk(), previous.score() + contribution));
        }
    }

    private record RankedChunk(AiKnowledgePort.RetrievedChunk chunk, double score) {
    }
}
