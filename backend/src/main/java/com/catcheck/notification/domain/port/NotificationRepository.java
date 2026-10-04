package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.Notification;

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

    /** G4 — hộp thư in-app, phân trang con trỏ {@code (created_at, id)} giảm dần (p8 §8.1.4). */
    Page listInbox(UUID userId, String cursor, int limit);

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
}
