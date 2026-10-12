package com.catcheck.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiChatRequest(
        UUID conversationId,
        UUID catId,
        @NotBlank @Size(max = 4000) String message) {
}
