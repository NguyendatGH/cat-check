package com.catcheck.ai.infrastructure;

import com.catcheck.ai.api.AiErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiErrorCodeConfiguration {

    @Bean
    ErrorCode conversationNotFound() {
        return AiErrorCode.CONVERSATION_NOT_FOUND;
    }

    @Bean
    ErrorCode aiMessageInvalid() {
        return AiErrorCode.AI_MESSAGE_INVALID;
    }

    @Bean
    ErrorCode aiProviderFailed() {
        return AiErrorCode.AI_PROVIDER_FAILED;
    }

    @Bean
    ErrorCode aiChatRateLimited() {
        return AiErrorCode.AI_CHAT_RATE_LIMITED;
    }

    @Bean
    ErrorCode aiRequestInProgress() {
        return AiErrorCode.AI_REQUEST_IN_PROGRESS;
    }
}
