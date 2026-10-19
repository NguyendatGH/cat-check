package com.catcheck.ai.application;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HybridRetrievalServiceTest {

    private static final UUID DOC_A = UUID.randomUUID();
    private static final UUID DOC_B = UUID.randomUUID();
    private static final UUID DOC_C = UUID.randomUUID();

    @Test
    void chunkFoundByBothBranchesOutranksChunksFoundByOne() {
        var shared = chunk(DOC_B, 0.9);
        StubKnowledge knowledge = new StubKnowledge(List.of(chunk(DOC_A, 3), shared), List.of(shared, chunk(DOC_C, 0.95)));

        List<AiKnowledgePort.RetrievedChunk> result = service(knowledge, true).retrieve("vi", "khay cát", 6);

        assertThat(result).extracting(AiKnowledgePort.RetrievedChunk::documentId).first().isEqualTo(DOC_B);
        assertThat(result).hasSize(3);
    }

    @Test
    void vectorOnlyCandidatesBelowTheSimilarityThresholdAreDropped() {
        StubKnowledge knowledge = new StubKnowledge(List.of(chunk(DOC_A, 2)), List.of(chunk(DOC_C, 0.5)));

        assertThat(service(knowledge, true).retrieve("vi", "khay cát", 6))
                .extracting(AiKnowledgePort.RetrievedChunk::documentId).containsExactly(DOC_A);
    }

    @Test
    void atMostTwoChunksPerDocumentReachThePrompt() {
        StubKnowledge knowledge = new StubKnowledge(
                List.of(chunk(DOC_A, 5), chunk(DOC_A, 4), chunk(DOC_A, 3), chunk(DOC_B, 2)), List.of());

        assertThat(service(knowledge, false).retrieve("vi", "khay cát", 6))
                .extracting(AiKnowledgePort.RetrievedChunk::documentId).containsExactly(DOC_A, DOC_A, DOC_B);
    }

    @Test
    void repeatedQuestionIsServedFromCacheUntilTheCorpusChanges() {
        StubKnowledge knowledge = new StubKnowledge(List.of(chunk(DOC_A, 2)), List.of());
        HybridRetrievalService service = service(knowledge, false);

        service.retrieve("vi", "Vệ sinh khay cát?", 6);
        service.retrieve("vi", "ve sinh khay cat", 6);
        assertThat(knowledge.calls).as("same terms after folding hit the cache").isEqualTo(1);

        service.corpusChanged();
        service.retrieve("vi", "ve sinh khay cat", 6);
        assertThat(knowledge.calls).isEqualTo(2);
    }

    @Test
    void questionMadeOnlyOfStopwordsDoesNotTouchTheDatabase() {
        StubKnowledge knowledge = new StubKnowledge(List.of(chunk(DOC_A, 2)), List.of());

        assertThat(service(knowledge, true).retrieve("vi", "bạn là ai vậy?", 6)).isEmpty();
        assertThat(knowledge.calls).isZero();
    }

    private static HybridRetrievalService service(StubKnowledge knowledge, boolean withEmbedding) {
        AiEmbeddingPort embedding = withEmbedding ? new AiEmbeddingPort() {
            public boolean available() { return true; }
            public String modelId() { return "stub"; }
            public float[] embedQuery(String text) { return new float[384]; }
            public List<float[]> embedPassages(List<String> texts) { return List.of(); }
        } : AiEmbeddingPort.NONE;
        return new HybridRetrievalService(knowledge, embedding, new AiRuntimeConfig(6, 8));
    }

    private static AiKnowledgePort.RetrievedChunk chunk(UUID documentId, double score) {
        return new AiKnowledgePort.RetrievedChunk(UUID.randomUUID(), "h", documentId, "t", "/u", "c" + score, 0, score);
    }

    private static final class StubKnowledge implements AiKnowledgePort {
        private final List<RetrievedChunk> lexical;
        private final List<RetrievedChunk> vector;
        int calls;

        StubKnowledge(List<RetrievedChunk> lexical, List<RetrievedChunk> vector) {
            this.lexical = lexical;
            this.vector = vector;
        }

        @Override
        public CandidateSet searchCandidates(String locale, List<String> terms, float[] queryEmbedding,
                                             String embeddingModel, int perBranch) {
            calls++;
            return new CandidateSet(lexical, queryEmbedding == null ? List.of() : vector);
        }

        public Conversation createConversation(UUID userId, UUID requestedId) { return null; }
        public Optional<UUID> saveUserMessage(UUID conversationId, String content, UUID requestKey) { return Optional.empty(); }
        public UUID saveAssistantMessage(UUID conversationId, String content, String provider, UUID requestKey) { return null; }
        public void releaseRequestKey(UUID requestKey) { }
        public Optional<AiChatService.ChatResult> findReply(UUID userId, UUID requestKey) { return Optional.empty(); }
        public List<String[]> history(UUID conversationId, int limit) { return new ArrayList<>(); }
        public void saveCitations(UUID messageId, List<RetrievedChunk> chunks) { }
        public SyncResult syncPublishedCareTips() { return new SyncResult(0, 0, 0); }
        public List<PendingChunk> chunksNeedingEmbedding(String embeddingModel, int limit) { return List.of(); }
        public void saveEmbeddings(String embeddingModel, Map<UUID, float[]> embeddings) { }
        public int eraseConversationContent(UUID userId) { return 0; }
    }
}
