package com.catcheck.ai.api.dto;

import com.catcheck.ai.application.AiKnowledgePort;

import java.time.Instant;
import java.util.UUID;

public record AiMessageResponse(
        UUID id,
        UUID conversationId,
        String role,
        String content,
        String provider,
        Instant createdAt) {

    public static AiMessageResponse from(AiKnowledgePort.MessageItem item) {
        return new AiMessageResponse(
                item.id(),
                item.conversationId(),
                item.role(),
                item.content(),
                item.provider(),
                item.createdAt());
    }
}
