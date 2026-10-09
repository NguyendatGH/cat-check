package com.catcheck.credit.domain.port;

import com.catcheck.credit.domain.ActivationRateLimitRule;

/**
 * Đếm giới hạn tần suất cho {@code POST /activations} (G3,
 * {@code docs/cat-rag-credit-pipeline.md} B2).
 *
 * <p>Là CỔNG chứ không phải dịch vụ: Bucket4j nằm ở {@code ..infrastructure..} nên
 * {@code ..application..} không được import nó (R2) — cùng quy ước với
 * {@code identity.domain.port.RateLimiter}.</p>
 *
 * <p><b>Vì sao có port riêng cho module {@code credit} thay vì dùng lại
 * {@code identity.domain.port.RateLimiter}:</b> {@code credit/package-info.java} khai
 * {@code allowedDependencies = { "shared", "audit::api", "notification::api" }} — không có
 * {@code identity}. Danh mục quy tắc của identity ({@code identity.domain.RateLimitRule}) là bản
 * chép bảng p11 §11.7.2 của RIÊNG module đó; thêm một quy tắc kích hoạt mã credit vào đó sẽ đặt
 * ngưỡng của module {@code credit} vào một enum mà {@code credit} không đọc được. Cả hai cài đặt
 * đều là Bucket4j + Caffeine in-memory theo đúng p11 §11.7.1 (MVP), nên không có sự thật nào bị
 * nhân đôi — chỉ có hai danh mục quy tắc cho hai module, giống hệt lý do
 * {@code shared.application.spi.PublicIngressRateLimiter} đã ghi.</p>
 */
public interface ActivationRateLimiter {

    /**
     * Tiêu một đơn vị trong thùng của {@code rule}.
     *
     * @param subject khoá theo {@link ActivationRateLimitRule#keyKind()} của quy tắc;
     *                {@code null} được phép khi caller không xác định được (thì bỏ qua quy tắc
     *                thay vì ném — ví dụ chưa có userId thì bỏ qua quy tắc theo IP vẫn còn bảo vệ)
     * @return {@code true} nếu được phép; {@code false} nếu đã hết hạn mức
     */
    boolean tryConsume(ActivationRateLimitRule rule, String subject);

    /**
     * Số giây còn phải chờ trước khi thùng có thêm 1 đơn vị.
     *
     * @return {@code 0} nếu không bị chặn, {@code > 0} nếu đang chặn. Dùng cho header
     *         {@code Retry-After}.
     */
    long secondsUntilRefill(ActivationRateLimitRule rule, String subject);

    /**
     * Cho phép trả về {@code 429} sớm nhất có thể.
     *
     * <p>Trả về {@code null} nếu không bị chặn.</p>
     */
    default RetryAfter rejectIfLimited(ActivationRateLimitRule rule, String subject) {
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
