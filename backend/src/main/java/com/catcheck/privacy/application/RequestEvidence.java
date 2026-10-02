package com.catcheck.privacy.application;

import java.util.UUID;

/**
 * Bằng chứng kênh lúc user hành động — ghi vào {@code consent_record}/
 * {@code policy_acknowledgment} (p15 §15.10-C: "phương thức xin đồng ý phải kiểm chứng
 * được về việc xác định chủ thể, thời điểm và nội dung").
 *
 * @param requestId  nối với {@code X-Request-Id} / {@code audit_log.request_id}
 * @param ipAddress IP của request, {@code null} khi không đọc được (ví dụ nội bộ)
 * @param userAgent User-Agent, {@code null} khi client không gửi
 */
public record RequestEvidence(
        String requestId,
        String ipAddress,
        String userAgent
) {

    public static RequestEvidence of(String requestId, String ipAddress, String userAgent) {
        return new RequestEvidence(
                requestId != null && requestId.length() > 64 ? requestId.substring(0, 64) : requestId,
                ipAddress,
                userAgent);
    }

    /** Bản rút gỉn cho job nền — không có IP/UA. */
    public static RequestEvidence system() {
        return new RequestEvidence(null, null, null);
    }
}
