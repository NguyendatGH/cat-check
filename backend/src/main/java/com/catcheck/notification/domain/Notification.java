package com.catcheck.notification.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Một dòng {@code notification} (p4 F2) — <b>bản ghi nghiệp vụ</b> "user này đã được thông báo
 * điều gì". Giao vận ("đã đẩy tới thiết bị nào, thử mấy lần") nằm ở {@link NotificationOutboxEntry}.
 *
 * @param payload        tham số render + {@code deepLink}; cũng là nơi giữ {@code hiddenAt}
 *                       (xem {@link #hidden()}) và {@code suppressedReason}
 * @param titleSnapshot  tiêu đề ĐÃ render — để inbox hiển thị đúng nội dung cũ sau khi template
 *                       đổi câu chữ (p12 §12.4)
 * @param dedupeKey      {@code <template>:<refType>:<refId>:<mốc>:<channel>} — UNIQUE ở DB, chặn
 *                       gửi trùng khi job chạy lại (p12 §12.4)
 */
public record Notification(
        UUID id,
        UUID userId,
        NotificationChannel channel,
        NotificationTemplate template,
        Map<String, Object> payload,
        String titleSnapshot,
        String bodySnapshot,
        NotificationStatus status,
        NotificationRefType refType,
        UUID refId,
        String dedupeKey,
        int attemptCount,
        Instant scheduledAt,
        Instant sentAt,
        Instant readAt,
        Instant createdAt) {

    /** Khoá trong {@code payload} đánh dấu user đã ẩn thông báo khỏi hộp thư (p8 G8). */
    public static final String HIDDEN_AT_KEY = "hiddenAt";

    /** Khoá trong {@code payload} ghi lý do {@code SUPPRESSED} (p4 F2 ghi chú nghiệp vụ). */
    public static final String SUPPRESSED_REASON_KEY = "suppressedReason";

    public Notification {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }

    public boolean unread() {
        return readAt == null;
    }

    /** p8 G8 "ẩn khỏi hộp thư (soft)" — p4 F2 không có cột riêng, xem javadoc repository. */
    public boolean hidden() {
        return payload.get(HIDDEN_AT_KEY) != null;
    }
}
