package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.Notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng {@code notification} (p4 F2). Mọi truy vấn của người dùng đều mang {@code user_id = ?}. */
public interface NotificationRepository {

    /**
     * Ghi một dòng. Va {@code uq_notification_dedupe} ⇒ trả {@link Optional#empty()} (job chạy
     * lại sau khi chết giữa chừng không gửi lần hai — p12 §12.4).
     */
    Optional<Notification> insertIfAbsent(Notification notification);

    /**
     * G4 — hộp thư in-app, phân trang con trỏ {@code (created_at, id)} giảm dần (p8 §8.1.4).
     *
     * @param cursor đã giải mã ở tầng service, {@code null} = trang đầu
     */
    Page listInbox(UUID userId, InboxCursor cursor, int limit);

    /** G5 — badge chuông. Dùng partial index {@code ix_notification_unread_inapp}. */
    long countUnread(UUID userId);

    Optional<Notification> findByIdForUser(UUID notificationId, UUID userId);

    /** G6 — trả về {@code false} nếu không có dòng nào (đã đọc rồi thì vẫn {@code true}). */
    boolean markRead(UUID notificationId, UUID userId);

    /** G7 — trả số dòng vừa đánh dấu. */
    int markAllRead(UUID userId);

    /** G8 — ẩn khỏi hộp thư. */
    boolean hide(UUID notificationId, UUID userId);

    /** Đổi trạng thái sau khi outbox push của thông báo này chạy xong. */
    void updateStatus(UUID notificationId, String status, String lastError);

    /** Trang kết quả dạng keyset. */
    record Page(List<Notification> items, String nextCursor, boolean hasMore) {
    }

    /**
     * Con trỏ keyset {@code (created_at, id)}, cùng khuôn với {@code JdbcScanQueryRepository} để
     * client chỉ phải hiểu một định dạng (p8 §8.1.4: cursor là chuỗi opaque). Giải mã ở tầng
     * service để cursor hỏng thành {@code 400} trước khi chạm repository.
     */
    record InboxCursor(Instant createdAt, UUID id) {

        public InboxCursor {
            if (createdAt == null || id == null) {
                throw new IllegalArgumentException("cursor thiếu trường bắt buộc");
            }
        }

        public String encode() {
            String raw = createdAt.toEpochMilli() + "." + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        /** Ném {@link IllegalArgumentException} khi chuỗi không phải do {@link #encode()} sinh ra. */
        public static InboxCursor decode(String cursor) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int separator = raw.indexOf('.');
                if (separator <= 0) {
                    throw new IllegalArgumentException("cursor thiếu dấu phân cách");
                }
                return new InboxCursor(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, separator))),
                        UUID.fromString(raw.substring(separator + 1)));
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("cursor không hợp lệ", ex);
            }
        }
    }
}
