package com.catcheck.identity.domain;

import java.time.LocalTime;

/**
 * Tuỳ chọn thông báo theo kênh và giờ im lặng — biểu diễn trong miền của bảng
 * {@code user_notification_preference} (p4 F4, tạo ở V13).
 *
 * <p>Tám field khớp ĐÚNG hợp đồng p8 §8.5 của {@code PUT /account/notification-preferences}:
 * {@code {attentionAlertChannel, creditAlertsEnabled, reportReadyEnabled,
 * imageRetentionWarningEnabled, normalResultEnabled, quietHoursEnabled, quietHoursStart,
 * quietHoursEnd}} — không thêm, không bớt, không đổi tên.</p>
 *
 * <p><b>Bản trước lưu ở {@code app_user.notification_prefs} (JSONB) với shape
 * {@code {push,email,inApp,types:{...}}} và đã ghi chú là "lệch p8, chờ phân xử".</b> Không
 * cần phân xử: theo thứ tự thẩm quyền ở {@code CLAUDE.md}, giữa hai part thì <i>part sở hữu
 * miền thắng</i>, và p8 sở hữu miền hợp đồng API. Lý do kỹ thuật của bản cũ ("bảng riêng chưa
 * có migration") cũng đã hết hiệu lực: V13 tạo {@code user_notification_preference} đúng tám
 * cột này.</p>
 *
 * <p>Đây KHÔNG phải consent. Tuỳ chọn mang tính pháp lý (email marketing, lưu ảnh) nằm ở
 * {@code consent_record} và append-only — p12 §12.9.2 cấm trộn hai thứ.</p>
 *
 * @param quietHoursStart giờ im lặng theo {@code app_user.timezone}. {@code start > end} là
 *                        HỢP LỆ và là trường hợp thường gặp (22:00 → 07:00 vắt qua nửa đêm) —
 *                        đừng "sửa" bằng cách ép {@code end > start} như một khoảng thời gian
 *                        thông thường
 */
public record NotificationPreferences(
        AttentionAlertChannel attentionAlertChannel,
        boolean creditAlertsEnabled,
        boolean reportReadyEnabled,
        boolean imageRetentionWarningEnabled,
        boolean normalResultEnabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd) {

    /** Mặc định lấy nguyên văn cột DEFAULT của {@code user_notification_preference} (p4 F4). */
    public static NotificationPreferences defaults() {
        return new NotificationPreferences(
                AttentionAlertChannel.PUSH_AND_INAPP,
                true,
                true,
                // Mặc định TẮT: mọi ảnh đều đến hạn 14 ngày nên bật mặc định là spam
                // (p12 §12.2.3).
                false,
                // Thông báo kết quả bình thường — mặc định tắt.
                false,
                true,
                LocalTime.of(22, 0),
                LocalTime.of(7, 0));
    }
}
