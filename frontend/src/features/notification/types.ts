/**
 * Hợp đồng JSON nhóm G (p8 §8.4.7 G4–G11) — đọc thẳng từ controller thật
 * `backend/src/main/java/com/catcheck/notification/api/**`, không suy đoán.
 *
 * ⚠ `NotificationView` của backend KHÔNG dùng tên cột của p4 F2: trả `title`/`body`/`read`
 * chứ không phải `titleSnapshot`/`bodySnapshot`/`readAt`-only. Đã ghi handoff H15.107.
 */

/** Khối `page` của envelope danh sách chuẩn (p8 §8.1.4), biến thể cursor. */
export interface CursorPage {
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

/**
 * Một dòng hộp thư in-app.
 *
 * `templateCode` là **tên enum** `NotificationTemplate` (VD `REMINDER_SCAN_DUE`), KHÔNG phải
 * chuỗi chấm `reminder.scan_due` mà p4 F2 / X1 mô tả — `NotificationTemplate.code()` trả
 * `name()`. Handoff H15.108.
 */
export interface NotificationItem {
  id: string;
  templateCode: string;
  title: string;
  body: string;
  deepLink: string | null;
  refType: string | null;
  refId: string | null;
  read: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationListResponse {
  items: NotificationItem[];
  page: CursorPage;
}

export interface UnreadCountResponse {
  unreadCount: number;
}

/** `platform` của p4 F3 — regex `WEB|ANDROID_PWA|IOS_PWA` ở `UpsertPushSubscriptionRequest`. */
export type PushPlatform = "WEB" | "ANDROID_PWA" | "IOS_PWA";

/**
 * Một thiết bị đang nhận push.
 *
 * Backend CỐ Ý không trả `fid`/`legacyToken` (cột PII, p12 §12.6.4a) ⇒ client không thể so
 * khớp "thiết bị này là máy đang dùng" bằng định danh. Xem `pushClient.ts` để biết cách lách.
 */
export interface PushSubscriptionItem {
  id: string;
  platform: PushPlatform;
  deviceLabel: string | null;
  lastSeenAt: string | null;
  lastSuccessAt: string | null;
  createdAt: string;
}

export interface PushSubscriptionListResponse {
  items: PushSubscriptionItem[];
  page: CursorPage;
}

/** Body của `PUT /push/subscriptions` (G10). Ít nhất một trong `fid`/`legacyToken`. */
export interface UpsertPushSubscriptionPayload {
  fid?: string;
  legacyToken?: string;
  platform?: PushPlatform;
  deviceLabel?: string;
}
