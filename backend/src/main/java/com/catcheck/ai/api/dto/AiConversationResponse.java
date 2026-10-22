package com.catcheck.ai.api.dto;

import com.catcheck.ai.application.AiKnowledgePort;

import java.time.Instant;
import java.util.UUID;

public record AiConversationResponse(
        UUID id,
        UUID userId,
        Instant createdAt,
        Instant updatedAt,
        String preview) {

    public static AiConversationResponse from(AiKnowledgePort.ConversationSummary summary) {
        return new AiConversationResponse(
                summary.id(),
                summary.userId(),
                summary.createdAt(),
                summary.updatedAt(),
                summary.preview());
    }
}
