package com.catcheck.ai.application;

import java.util.List;
import java.util.UUID;

public interface AiKnowledgePort {
    record RetrievedChunk(UUID documentId, String title, String sourceUrl, String content, int rank) {
    }

    record Conversation(UUID id, UUID userId) {
    }

    boolean hasDocuments();

    List<RetrievedChunk> search(String locale, String query, int limit);

    Conversation createConversation(UUID userId, UUID requestedId);

    UUID saveMessage(UUID conversationId, String role, String content, String provider);

    List<String[]> history(UUID conversationId, int limit);

    void saveCitations(UUID messageId, List<RetrievedChunk> chunks);

    int syncPublishedCareTips();
}
