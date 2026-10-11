package com.catcheck.community.domain.port;

import com.catcheck.community.domain.CommunityRateLimitRule;

/**
 * Đếm giới hạn tần suất cho các luồng ghi dễ bị spam của community (đăng bài, bình luận, report).
 *
 * <p>Là CỔNG chứ không phải dịch vụ: Bucket4j nằm ở {@code ..infrastructure..} nên
 * {@code ..application..} không được import nó trực tiếp — cùng quy ước với
 * {@code credit.domain.port.ActivationRateLimiter} và {@code identity.domain.port.RateLimiter}.
 * Copy nguyên hình dạng (không dùng chung instance) vì community không được phép phụ thuộc module
 * {@code credit}/{@code identity} ({@code community/package-info.java} chỉ cho
 * {@code allowedDependencies = { "shared", "audit::api" }}).</p>
 */
public interface CommunityRateLimiter {

    /**
     * Tiêu một đơn vị trong thùng của {@code rule}.
     *
     * @param subject khoá theo userId; {@code null} được phép khi caller không xác định được
     *                (thì bỏ qua quy tắc thay vì ném)
     * @return {@code true} nếu được phép; {@code false} nếu đã hết hạn mức
     */
    boolean tryConsume(CommunityRateLimitRule rule, String subject);

    /**
     * Số giây còn phải chờ trước khi thùng có thêm 1 đơn vị.
     *
     * @return {@code 0} nếu không bị chặn, {@code > 0} nếu đang chặn. Dùng cho header
     *         {@code Retry-After}.
     */
    long secondsUntilRefill(CommunityRateLimitRule rule, String subject);

    /**
     * Cho phép trả về {@code 429} sớm nhất có thể.
     *
     * <p>Trả về {@code null} nếu không bị chặn.</p>
     */
    default RetryAfter rejectIfLimited(CommunityRateLimitRule rule, String subject) {
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
