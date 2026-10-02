// features/settings — tuỳ chọn thông báo (B11/B12) + dữ liệu thiết kế cho trang Cài đặt.
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  useNotificationPreferences,
  useUpdateNotificationPreferences,
  NOTIFICATION_PREFERENCES_KEY,
} from "./hooks";

export type {
  NotificationPreferences,
  AttentionAlertChannel,
  NotificationToggleKey,
} from "./types";
