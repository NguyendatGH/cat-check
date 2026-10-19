package com.catcheck.ai.application;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface AiKnowledgePort {
    /** {@code score}: lexical = điểm ts_rank/trigram, vector = cosine similarity; chỉ so sánh trong cùng nhánh. */
    record RetrievedChunk(UUID chunkId, String chunkHash, UUID documentId, String title, String sourceUrl,
                          String content, int rank, double score) {

        public RetrievedChunk(UUID documentId, String title, String sourceUrl, String content, int rank) {
            this(null, null, documentId, title, sourceUrl, content, rank, 0);
        }

        public RetrievedChunk withRank(int newRank) {
            return new RetrievedChunk(chunkId, chunkHash, documentId, title, sourceUrl, content, newRank, score);
        }
    }

    record Conversation(UUID id, UUID userId) {
    }

    /** Both candidate lists come from one database round trip; {@code vector} is empty without an embedding. */
    record CandidateSet(List<RetrievedChunk> lexical, List<RetrievedChunk> vector) {
    }

    record PendingChunk(UUID id, String content) {
    }

    record SyncResult(int documents, int changedDocuments, int removedDocuments) {
        public boolean changed() {
            return changedDocuments > 0 || removedDocuments > 0;
        }
    }

    /**
     * @param terms           từ nội dung đã bỏ dấu ({@link AiText#contentTerms}); rỗng thì không có nhánh lexical
     * @param queryEmbedding  null khi không có model embedding
     */
    CandidateSet searchCandidates(String locale, List<String> terms, float[] queryEmbedding, String embeddingModel,
                                  int perBranch);

    Conversation createConversation(UUID userId, UUID requestedId);

    /**
     * Ghi câu hỏi. Rỗng khi {@code requestKey} đã được dùng (request trùng đang chạy hoặc đã xong); câu hỏi
     * mồ côi quá hạn (không có câu trả lời) được nhả key và ghi lại một lần.
     */
    Optional<UUID> saveUserMessage(UUID conversationId, String content, UUID requestKey);

    /** Bỏ {@code requestKey} khỏi câu hỏi của lượt hỏi thất bại để retry cùng key chạy lại được. */
    void releaseRequestKey(UUID requestKey);

    UUID saveAssistantMessage(UUID conversationId, String content, String provider, UUID requestKey);

    /** Câu trả lời đã lưu cho request key của chính user này (retry trả lại đúng kết quả cũ). */
    Optional<AiChatService.ChatResult> findReply(UUID userId, UUID requestKey);

    List<String[]> history(UUID conversationId, int limit);

    void saveCitations(UUID messageId, List<RetrievedChunk> chunks);

    /** Reindex gia tăng theo hash: tài liệu/chunk không đổi thì không ghi lại. */
    SyncResult syncPublishedCareTips();

    List<PendingChunk> chunksNeedingEmbedding(String embeddingModel, int limit);

    void saveEmbeddings(String embeddingModel, Map<UUID, float[]> embeddings);

    int eraseConversationContent(UUID userId);
}
