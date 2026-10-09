package com.catcheck.ai.domain.port;

import com.catcheck.ai.domain.AiChatRateLimitRule;

/**
 * Đếm giới hạn tần suất cho {@code POST /api/v1/ai/chat} (nhiệm vụ wiring A2-A4,
 * {@code docs/cat-rag-credit-pipeline.md} mục A4-(4)).
 *
 * <p>Là CỔNG chứ không phải dịch vụ: Bucket4j nằm ở {@code ..infrastructure..} nên
 * {@code ..application..} không được import nó (R2) — cùng quy ước với
 * {@code identity.domain.port.RateLimiter} và {@code credit.domain.port.ActivationRateLimiter}.</p>
 *
 * <p><b>Vì sao có port riêng cho module {@code ai} thay vì dùng lại
 * {@code credit.domain.port.ActivationRateLimiter} hay {@code identity.domain.port.RateLimiter}:</b>
 * {@code ai/package-info.java} khai {@code allowedDependencies = { "shared", "cat::api", "scan::api",
 * "insight::api", "reminder::api", "credit::api" }} — không có quyền phụ thuộc
 * {@code credit.domain.port}/{@code identity.domain.port} (chỉ named interface {@code credit::api}
 * được phép, không phải {@code credit.domain}). Thêm một quy tắc chat AI vào danh mục của module
 * khác sẽ đặt ngưỡng của {@code ai} vào một enum mà {@code ai} không đọc được. Cả ba cài đặt đều
 * là Bucket4j + Caffeine in-memory (MVP), nên không có sự thật nào bị nhân đôi — chỉ có ba danh
 * mục quy tắc cho ba module, giống hệt lý do {@code ActivationRateLimiter} đã ghi.</p>
 */
public interface AiChatRateLimiter {

    /**
     * Tiêu một đơn vị trong thùng của {@code rule}.
     *
     * @param subject khoá theo {@code userId}; {@code null}/rỗng được phép khi caller không xác
     *                định được (thì bỏ qua quy tắc thay vì ném)
     * @return {@code true} nếu được phép; {@code false} nếu đã hết hạn mức
     */
    boolean tryConsume(AiChatRateLimitRule rule, String subject);

    /**
     * Số giây còn phải chờ trước khi thùng có thêm 1 đơn vị.
     *
     * @return {@code 0} nếu không bị chặn, {@code > 0} nếu đang chặn. Dùng cho header
     *         {@code Retry-After}.
     */
    long secondsUntilRefill(AiChatRateLimitRule rule, String subject);

    /**
     * Cho phép trả về {@code 429} sớm nhất có thể.
     *
     * <p>Trả về {@code null} nếu không bị chặn.</p>
     */
    default RetryAfter rejectIfLimited(AiChatRateLimitRule rule, String subject) {
        return tryConsume(rule, subject) ? null : new RetryAfter(secondsUntilRefill(rule, subject));
    }

    /** Số giây client phải chờ lại trước khi thử lại. */
    record RetryAfter(long seconds) {

        /** Header {@code Retry-After} phải là số giây nguyên, tối thiểu 1. */
        public long headerSeconds() {
            return Math.max(1, seconds);
        }
    }
}
