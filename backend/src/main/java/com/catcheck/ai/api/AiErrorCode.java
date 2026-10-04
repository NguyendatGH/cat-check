package com.catcheck.ai.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AiErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND("conversation-not-found", HttpStatus.NOT_FOUND),
    AI_MESSAGE_INVALID("ai-message-invalid", HttpStatus.BAD_REQUEST),
    AI_PROVIDER_FAILED("ai-provider-failed", HttpStatus.BAD_GATEWAY);

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
        return URI.create("https://catcheck.vn/problems/" + slug);
    }
}
