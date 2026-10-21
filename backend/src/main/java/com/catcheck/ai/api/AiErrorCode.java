package com.catcheck.ai.api;

import com.catcheck.shared.error.ErrorCode;
import com.catcheck.shared.error.ProblemTypes;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AiErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND("conversation-not-found", HttpStatus.NOT_FOUND),
    AI_MESSAGE_INVALID("ai-message-invalid", HttpStatus.BAD_REQUEST),
    AI_PROVIDER_FAILED("ai-provider-failed", HttpStatus.BAD_GATEWAY),

    AI_CHAT_RATE_LIMITED("ai-chat-rate-limited", HttpStatus.TOO_MANY_REQUESTS),
    /** Request cùng Idempotency-Key đang được xử lý — không gọi provider lần hai. */
    AI_REQUEST_IN_PROGRESS("ai-request-in-progress", HttpStatus.CONFLICT),

    ACCOUNT_RESTRICTED("common/account-restricted", HttpStatus.FORBIDDEN);

    private final String slug;
    private final HttpStatus status;

    AiErrorCode(String slug, HttpStatus status) {
        this.slug = slug;
        this.status = status;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public URI typeUri() {
        return ProblemTypes.of(slug);
    }
}
