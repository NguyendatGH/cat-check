/**
 * Hợp đồng `GET|PUT /account/notification-preferences` (B11/B12).
 *
 * Tám field phẳng theo ĐÚNG p8 §8.5, nguồn dữ liệu là bảng `user_notification_preference`
 * mà p8 B11 chỉ đích danh (tạo ở `V13__notification.sql`).
 *
 * Bản trước khai `{push, email, inApp, types:{...}}` theo cột JSONB `app_user.notification_prefs`
 * và tự ghi chú là "lệch p8, chờ phân xử". Không cần phân xử: theo thứ tự thẩm quyền ở
 * CLAUDE.md, giữa hai part thì part SỞ HỮU MIỀN thắng, và p8 sở hữu miền hợp đồng API. Lý do
 * kỹ thuật của bản cũ ("bảng riêng chưa có migration") cũng đã hết hiệu lực.
 */

/**
 * Kênh nhận cảnh báo "kết quả cần chú ý".
 *
 * CỐ Ý không có giá trị tắt: rule cảnh báo là lý do tồn tại của sản phẩm (quyết định #10),
 * p4 F4 chỉ cho user chọn kênh. Server trả 400 cho mọi giá trị khác hai cái này.
 */
export type AttentionAlertChannel = "PUSH_AND_INAPP" | "INAPP_ONLY";

export interface NotificationPreferences {
  attentionAlertChannel: AttentionAlertChannel;
  /** Nhắc credit sắp hết hạn (T-48h và T-6h). Tắt cả hai thì có thể mất credit mà không biết. */
  creditAlertsEnabled: boolean;
  /** Báo khi bản xuất PDF đã sẵn sàng tải. */
  reportReadyEnabled: boolean;
  /** Báo ảnh sắp đến hạn xoá 14 ngày. Mặc định TẮT — mọi ảnh đều đến hạn nên bật là spam. */
  imageRetentionWarningEnabled: boolean;
  /** Báo cả những lần quét cho kết quả bình thường. Mặc định TẮT. */
  normalResultEnabled: boolean;
  quietHoursEnabled: boolean;
  /** `"HH:mm"` theo múi giờ tài khoản. */
  quietHoursStart: string;
  /** `"HH:mm"`. `start > end` là HỢP LỆ — giờ im lặng vắt qua nửa đêm (22:00 → 07:00). */
  quietHoursEnd: string;
}

/** Ba công tắc boolean độc lập — dùng để dựng danh sách mà không lặp lại tên khoá. */
export type NotificationToggleKey =
  "creditAlertsEnabled" | "reportReadyEnabled" | "imageRetentionWarningEnabled" | "normalResultEnabled";
