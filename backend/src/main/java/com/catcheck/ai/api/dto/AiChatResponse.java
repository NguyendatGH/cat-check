package com.catcheck.ai.api.dto;

import com.catcheck.ai.application.AiChatService;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AiChatResponse(
        UUID conversationId,
        UUID messageId,
        String answer,
        String provider,
        List<AiCitationResponse> citations,
        Instant createdAt) {

    public static AiChatResponse from(AiChatService.ChatResult result) {
        return new AiChatResponse(
                result.conversationId(), result.messageId(), result.answer(), result.provider(),
                result.citations().stream().map(AiCitationResponse::from).toList(), result.createdAt());
    }
}
