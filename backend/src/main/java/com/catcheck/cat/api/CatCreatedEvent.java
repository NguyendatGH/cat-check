package com.catcheck.cat.api;

import java.time.Instant;
import java.util.UUID;

/**
 * D2 — vừa tạo hồ sơ mèo.
 *
 * <p>Module onboarding/notification dùng để chúc mừng và gợi ý bước tiếp theo (tạo mèo là cột mốc
 * đầu tiên của {@code app_user.onboarding_status}).</p>
 *
 * @param catId         hồ sơ vừa tạo
 * @param ownerId       chủ sở hữu
 * @param publicCode    mã hiển thị để in ra bảng tên mèo
 * @param isFirstCat    mèo đầu tiên của tài khoản — onboarding gắn cờ này để ăn mừng khác với các
 *                      lần sau
 * @param isPrimary     đã được đặt làm mèo chính hay chưa (mèo đầu tiên luôn là mèo chính)
 * @param occurredAt    thời điểm phát, lấy từ {@code Clock} đã inject
 */
public record CatCreatedEvent(
        UUID catId,
        UUID ownerId,
        String publicCode,
        boolean isFirstCat,
        boolean isPrimary,
        Instant occurredAt) implements CatEvent {

    @Override
    public String eventType() {
        return "CAT_CREATED";
    }
}
