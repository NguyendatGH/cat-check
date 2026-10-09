package com.catcheck.ai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * @param catId tuỳ chọn — khi có, câu hỏi được làm giàu bằng ngữ cảnh của MỘT bé mèo cụ thể
 *              (xem {@code ai.application.CatContextPort}). Mèo không tồn tại hoặc không thuộc
 *              người dùng hiện tại được xử lý y hệt "không gửi catId" — không ném lỗi, không lộ
 *              thông tin tồn tại của mèo (xem {@code AiChatService#chat}).
 */
public record AiChatRequest(
        UUID conversationId,
        UUID catId,
        @NotBlank @Size(max = 4000) String message) {
}
