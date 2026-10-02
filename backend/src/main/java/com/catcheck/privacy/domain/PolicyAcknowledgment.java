package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Ghi nhận "đã đọc và hiểu" một văn bản pháp lý — dòng {@code policy_acknowledgment}
 * (p4 B4).
 *
 * <p>Đây là bằng chứng <b>đã thông báo</b> (disclaimer y tế, Điều khoản), KHÔNG phải căn cứ
 * xử lý dữ liệu cá nhân — không được trộn vào {@code consent_record} (p4 B2 ghi chú nghiệp
 * vụ). Append-only ở tầng DB như consent_record.</p>
 *
 * @param id               UUID v7
 * @param userId           FK app_user(id) ON DELETE RESTRICT
 * @param policyVersionId  bản chính sách đã đọc — FK policy_version(id)
 * @param policyHash        snapshot nội dung đã đọc
 * @param surface          điểm chạm (onboarding_disclaimer/result_screen_footer/terms_register)
 * @param occurredAt       thời điểm xác nhận
 * @param ipAddress        IP lúc xác nhận
 * @param userAgent        User-Agent lúc xác nhận
 * @param createdAt        mốc ghi dòng
 */
public record PolicyAcknowledgment(
        UUID id,
        UUID userId,
        UUID policyVersionId,
        String policyHash,
        PolicySurface surface,
        Instant occurredAt,
        String ipAddress,
        String userAgent,
        Instant createdAt
) {

    public PolicyAcknowledgment {
        if (id == null || userId == null || policyVersionId == null || policyHash == null) {
            throw new IllegalArgumentException("policyAcknowledgment thiếu trường bắt buộc");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("policyAcknowledgment.occurredAt bắt buộc");
        }
    }
}
