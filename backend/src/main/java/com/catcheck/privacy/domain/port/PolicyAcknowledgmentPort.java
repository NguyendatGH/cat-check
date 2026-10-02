package com.catcheck.privacy.domain.port;

import com.catcheck.privacy.domain.PolicyAcknowledgment;
import com.catcheck.privacy.domain.PolicySurface;

import java.util.UUID;

/**
 * Cổng ghi nhận "đã đọc" ({@code policy_acknowledgment}, p4 B4) — append-only như
 * {@code consent_record}. UNIQUE (user_id, policy_version_id, surface) để một bản chỉ
 * ghi một lần cho một điểm chạm.
 */
public interface PolicyAcknowledgmentPort {

    /** INSERT một ghi nhận. Trùng (user, version, surface) ⇒ bỏ qua thay vì lỗi — ack là idempotent. */
    void append(PolicyAcknowledgment acknowledgment);

    /** User này đã xác nhận bản này ở điểm chạm đó chưa — kiểm ở mỗi lần vào màn kết quả (p4 B4). */
    boolean exists(UUID userId, UUID policyVersionId, PolicySurface surface);
}
