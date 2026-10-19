
export interface CursorPage {
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

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

export type PushPlatform = "WEB" | "ANDROID_PWA" | "IOS_PWA";

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

export interface UpsertPushSubscriptionPayload {
  fid?: string;
  legacyToken?: string;
  platform?: PushPlatform;
  deviceLabel?: string;
}
