package com.catcheck.ai.api;

import com.catcheck.shared.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.net.URI;

public enum AiErrorCode implements ErrorCode {
    CONVERSATION_NOT_FOUND("conversation-not-found", HttpStatus.NOT_FOUND),
    AI_MESSAGE_INVALID("ai-message-invalid", HttpStatus.BAD_REQUEST),
    AI_PROVIDER_FAILED("ai-provider-failed", HttpStatus.BAD_GATEWAY),

    /**
     * 429 — vượt hạn mức gọi {@code POST /ai/chat} theo {@code userId} (nhiệm vụ wiring A2-A4,
     * {@code docs/cat-rag-credit-pipeline.md} A4-(4); xem {@code ai.domain.AiChatRateLimitRule}
     * cho lý do chọn ngưỡng — CHƯA có mục p11 tương ứng vì {@code context/} đã bị xoá).
     * Tham số: {@code ruleName} (chỉ để log/debug, không phải PII).
     */
    AI_CHAT_RATE_LIMITED("ai-chat-rate-limited", HttpStatus.TOO_MANY_REQUESTS),

    /**
     * I19 (p4 §4.4.9) — tài khoản {@code RESTRICTED} chỉ đọc: không lưu tin nhắn, không gọi LLM.
     * Trùng {@code code()} với {@code community.api.CommunityErrorCode.ACCOUNT_RESTRICTED} có chủ
     * đích và cùng lý do: KHÔNG đăng ký bean trong {@code AiErrorCodeConfiguration} (identity đã
     * đăng ký mã này, đăng ký lại làm {@code ErrorCodeRegistry.validate} chặn khởi động); câu dịch
     * lấy từ bundle {@code messages/identity}.
     */
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
        return URI.create("https://catcheck.vn/problems/" + slug);
    }
}
