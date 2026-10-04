package com.catcheck.notification.domain.port;

import com.catcheck.notification.domain.UserNotificationPreference;

import java.util.UUID;

/**
 * ĐỌC {@code user_notification_preference} (p4 F4).
 *
 * <p><b>Chỉ đọc, có chủ ý.</b> Luồng GHI thuộc module {@code identity} (p8 B11
 * {@code PUT /account/notification-preferences}, xem {@code JdbcUserAccountRepository}). Dựng
 * thêm một đường ghi ở đây sẽ tạo hai nguồn sự thật cho cùng một bảng.</p>
 */
public interface NotificationPreferenceRepository {

    /**
     * Dòng được tạo cùng lúc với {@code app_user} (p4 F4: 1-1 bắt buộc). Nếu vì lý do nào đó
     * chưa có, trả {@link UserNotificationPreference#defaults()} thay vì ném — thiếu tuỳ chọn
     * không được làm hỏng việc gửi thông báo an toàn.
     */
    UserNotificationPreference findByUserIdOrDefaults(UUID userId);
}
