package com.catcheck.privacy.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một sự kiện hành vi sản phẩm — dòng {@code user_activity_log} (p4 B11).
 *
 * <p><b>Bảng rủi ro pháp lý cao nhất trong nhóm B</b> (p15 §15.2.4): "lịch sử dùng app" có
 * thể bị coi là dữ liệu cá nhân nhạy cảm. Hệ quả bắt buộc (p15 REQ-PRIV-02): chỉ ghi khi
 * user đồng ý {@code PRODUCT_ANALYTICS} (mặc định TẮT), không ghi khi tài khoản RESTRICTED,
 * xoá cứng khi xoá tài khoản. {@code props} không chứa PII, không chứa giá trị pH (I29).</p>
 *
 * @param id          UUID v7
 * @param userId      chủ sở hữu — FK app_user(id) ON DELETE CASCADE; null = phiên khách
 * @param eventCode   'scan.started', 'export.requested'…
 * @param props       thuộc tính sự kiện — KHÔNG chứa PII, KHÔNG chứa giá trị pH (I29)
 * @param occurredAt  thời điểm sự kiện
 * @param createdAt   mốc ghi dòng
 */
public record UserActivityLog(
        UUID id,
        UUID userId,
        String eventCode,
        Map<String, Object> props,
        Instant occurredAt,
        Instant createdAt
) {

    public UserActivityLog {
        if (id == null || eventCode == null || occurredAt == null) {
            throw new IllegalArgumentException("userActivityLog thiếu trường bắt buộc");
        }
        props = props == null ? Map.of() : Map.copyOf(props);
    }
}
