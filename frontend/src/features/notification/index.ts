// features/notification — hộp thư in-app + thiết bị nhận push (p8 §8.4.7 nhóm G, G4–G11).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: code nằm ở các file root của feature (không thư mục con) theo khuôn mẫu
// bắt buộc của `features/insight` / `features/privacy`.
export {
  notificationKeys,
  useNotificationInbox,
  useUnreadNotificationCount,
  useMarkNotificationRead,
  useMarkAllNotificationsRead,
  useHideNotification,
  usePushSubscriptions,
  useUpsertPushSubscription,
  useRevokePushSubscription,
} from "./hooks";

export { NotificationUnreadBadge, PushDevicesCard } from "./components";

export {
  PushRegistrationError,
  currentDeviceLabel,
  currentPlatform,
  pushAvailability,
  readLocalSubscriptionId,
  registerThisDevice,
  unregisterThisDevice,
  writeLocalSubscriptionId,
} from "./pushClient";
export type { PushAvailability, PushBlockReason } from "./pushClient";

export { apiFetch as notificationApiFetch } from "./api";

export type {
  CursorPage,
  NotificationItem,
  NotificationListResponse,
  PushPlatform,
  PushSubscriptionItem,
  PushSubscriptionListResponse,
  UnreadCountResponse,
  UpsertPushSubscriptionPayload,
} from "./types";
