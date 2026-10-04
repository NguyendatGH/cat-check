package com.catcheck.notification.api;

import java.util.Map;
import java.util.UUID;

/**
 * Yêu cầu phát một thông báo cho một user đã có tài khoản.
 *
 * <p><b>{@code templateCode} là String, không phải enum</b>, để module gọi không phải import
 * {@code notification.domain} (Modulith chỉ mở named interface {@code notification::api}).
 * {@code NotificationService} tra registry {@code NotificationTemplate} và ném
 * {@code NOTIFICATION_TEMPLATE_UNKNOWN} nếu mã lạ — đúng p4 §4.4.7 ("validate ở service theo
 * registry template").</p>
 *
 * @param payload   tham số render; {@code deepLink} nằm trong đây và p12 §12.2.7 bắt buộc nó
 *                  phải là một route thật của p9 §9.4
 * @param title     tiêu đề đã render (snapshot). p12 §12.2.1: banner push KHÔNG được nêu giá
 *                  trị pH, tên phân loại hay mức độ bất thường
 * @param dedupeKey khoá chống gửi trùng, quy ước {@code <template>:<refType>:<refId>:<mốc>}
 *                  (p12 §12.4). {@code null} = không chống trùng ở tầng DB
 */
public record NotificationRequest(
        UUID userId,
        String templateCode,
        Map<String, Object> payload,
        String title,
        String body,
        String refType,
        UUID refId,
        String dedupeKey) {

    public NotificationRequest {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public static NotificationRequest of(UUID userId, String templateCode, String title, String body,
                                         Map<String, Object> payload) {
        return new NotificationRequest(userId, templateCode, payload, title, body, null, null, null);
    }
}
