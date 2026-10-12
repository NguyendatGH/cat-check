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
export { usePushSubscriptionRefresh } from "./pushRefresh";

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
