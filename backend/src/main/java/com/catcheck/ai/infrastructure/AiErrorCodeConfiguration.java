package com.catcheck.ai.infrastructure;

import com.catcheck.ai.api.AiErrorCode;
import com.catcheck.shared.error.ErrorCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Đăng ký {@link AiErrorCode} vào {@code shared.error.ErrorCodeRegistry}.
 *
 * <p><b>Tại sao không dùng {@code @Bean List<ErrorCode>}</b>: xem javadoc
 * {@code credit.infrastructure.CreditErrorCodeConfiguration} — {@code ErrorCodeRegistry} gom mọi
 * bean kiểu {@code ErrorCode}, một bean trả {@code List} không bao giờ được gom. Vì vậy mỗi hằng
 * enum là một bean {@code ErrorCode} riêng, đúng khuôn của {@code CreditErrorCodeConfiguration}/
 * {@code IdentityErrorCodeConfiguration}.</p>
 *
 * <p><b>Sửa một lỗ hổng có sẵn, phát hiện khi làm phần rate limit (A4 mục 5):</b> ba mã cũ
 * ({@code CONVERSATION_NOT_FOUND}, {@code AI_MESSAGE_INVALID}, {@code AI_PROVIDER_FAILED}) trước
 * đây CHƯA từng được đăng ký vào registry — {@code ErrorCodeRegistry} chưa bao giờ kiểm trùng mã
 * cho module {@code ai}, và không có bản dịch nào trong {@code messages/ai_*.properties} (file đó
 * còn chưa tồn tại) nên {@code MessageResolver} luôn trả về chính mã (vd {@code "CONVERSATION_NOT_FOUND"})
 * cho người dùng thay vì câu đã dịch. Đăng ký đủ bốn mã ở đây tiện thể đóng khoảng trống này —
 * không đổi hành vi nghiệp vụ, chỉ thêm dịch + kiểm trùng mã lúc khởi động.</p>
 */
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
}
