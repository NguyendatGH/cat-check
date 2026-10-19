package com.catcheck.credit.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Module identity phát khi một tài khoản NGƯỜI MUA (chỉ vai trò USER) vừa dùng được lần đầu:
 * đăng ký email đã xác minh OTP, hoặc tạo mới qua Google OAuth. Module credit nghe để tặng quà
 * chào mừng. Listener đồng bộ, cùng transaction với việc tạo/xác minh tài khoản.
 */
public record BuyerAccountActivatedEvent(UUID userId, SignupMethod method, Instant occurredAt) {

    public BuyerAccountActivatedEvent {
        if (userId == null || method == null || occurredAt == null) {
            throw new IllegalArgumentException("buyerAccountActivatedEvent thiếu trường bắt buộc");
        }
    }

    public enum SignupMethod {
        EMAIL,
        GOOGLE
    }
}
