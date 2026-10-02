package com.catcheck.privacy.domain.port;

import java.util.UUID;

/**
 * Cổng đọc trạng thái consent hiện hành — dùng cho consent gate của các module khác
 * (ví dụ bật push khi chưa đồng ý {@code HEALTH_REMINDER_PUSH} ⇒ p8
 * {@code CONSENT_REQUIRED}).
 *
 * <p>Không có consent nào được ghi thì trả {@code false} — mặc định TẮT, đúng tinh thần
 * "im lặng không phải là đồng ý" (p15 §15.3.1 C4). Module khác muốn dùng cổng này phải
 * khai {@code privacy} (hoặc {@code privacy::api}) trong {@code allowedDependencies} của
 * mình — xem {@code docs/handovers/A2.md}.</p>
 */
public interface ConsentStatePort {

    /** Đang hiệu lực = dòng mới nhất của cặp (user, purpose) là {@code GRANTED}. */
    boolean isGranted(UUID userId, String purposeCode);
}
