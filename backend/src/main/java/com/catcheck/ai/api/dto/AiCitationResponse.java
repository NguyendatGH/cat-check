package com.catcheck.ai.api.dto;

import com.catcheck.ai.application.AiChatService;

public record AiCitationResponse(String title, String sourceUrl, int rank) {
    public static AiCitationResponse from(AiChatService.Citation citation) {
        return new AiCitationResponse(citation.title(), citation.sourceUrl(), citation.rank());
    }
}
