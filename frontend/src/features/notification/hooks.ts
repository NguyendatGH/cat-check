import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type UseInfiniteQueryResult,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch } from "./api";
import type {
  NotificationListResponse,
  PushSubscriptionItem,
  PushSubscriptionListResponse,
  UnreadCountResponse,
  UpsertPushSubscriptionPayload,
} from "./types";

/**
 * Hooks react-query cho nhóm G (p8 §8.4.7 G4–G11): hộp thư in-app + thiết bị nhận push.
 *
 * Hai luật chi phối toàn bộ file:
 *  1. **Mọi mutation hộp thư phải invalidate CẢ `inbox` LẪN `unreadCount`.** Badge chuông là
 *     một query riêng (`GET /notifications/unread-count`, dùng partial index của p4 F2) —
 *     nó KHÔNG tự giảm khi một dòng trong danh sách đổi sang đã đọc. Invalidate ở gốc
 *     `notificationKeys.all` phủ cả hai.
 *  2. **Phân trang là cursor, không phải offset** (p8 §8.1.4) ⇒ `useInfiniteQuery` với
 *     `getNextPageParam` đọc `page.nextCursor`, và `hasMore` là thứ quyết định còn trang hay
 *     không — `nextCursor` có thể khác null ở trang cuối.
 */

const INBOX_PAGE_SIZE = 20;

export const notificationKeys = {
  all: ["notification"] as const,
  inbox: () => [...notificationKeys.all, "inbox"] as const,
  unreadCount: () => [...notificationKeys.all, "unread-count"] as const,
  pushSubscriptions: () => [...notificationKeys.all, "push-subscriptions"] as const,
};

/**
 * G4 — `GET /notifications?cursor=&limit=`.
 *
 * @param enabled đặt `false` cho khách chưa đăng nhập; endpoint yêu cầu phiên nên gọi khi
 *                chưa đăng nhập chắc chắn 401/403.
 */
export function useNotificationInbox(enabled = true): UseInfiniteQueryResult<InfiniteData<NotificationListResponse>> {
  return useInfiniteQuery({
    enabled,
    queryKey: notificationKeys.inbox(),
    queryFn: ({ pageParam }: { pageParam: string | null }) => {
      const qs = new URLSearchParams({ limit: String(INBOX_PAGE_SIZE) });
      if (pageParam) qs.set("cursor", pageParam);
      return apiFetch<NotificationListResponse>(`/notifications?${qs.toString()}`);
    },
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage: NotificationListResponse) =>
      lastPage.page.hasMore ? lastPage.page.nextCursor : undefined,
  });
}

/** G5 — `GET /notifications/unread-count`. Nguồn duy nhất của badge chuông. */
export function useUnreadNotificationCount(enabled = true): UseQueryResult<UnreadCountResponse> {
  return useQuery({
    enabled,
    queryKey: notificationKeys.unreadCount(),
    queryFn: () => apiFetch<UnreadCountResponse>("/notifications/unread-count"),
    // Badge nằm ở khung ứng dụng (mọi trang). Giữ 60s để điều hướng trong app không bắn
    // request mỗi lần đổi route; mọi thao tác đọc/ẩn đều invalidate ngay nên không bị lệch.
    staleTime: 60 * 1000,
    retry: false,
  });
}

/** G6 — `POST /notifications/{id}/read` (204). Idempotent ở server. */
export function useMarkNotificationRead(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: string) =>
      apiFetch<undefined>(`/notifications/${notificationId}/read`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

/** G7 — `POST /notifications/read-all` (204). */
export function useMarkAllNotificationsRead(): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/notifications/read-all", { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

/** G8 — `DELETE /notifications/{id}` (204). Ẩn khỏi hộp thư, bản ghi vẫn còn ở server. */
export function useHideNotification(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (notificationId: string) =>
      apiFetch<undefined>(`/notifications/${notificationId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

/** G9 — `GET /push/subscriptions`. Không phân trang (trần cứng 10 thiết bị). */
export function usePushSubscriptions(enabled = true): UseQueryResult<PushSubscriptionListResponse> {
  return useQuery({
    enabled,
    queryKey: notificationKeys.pushSubscriptions(),
    queryFn: () => apiFetch<PushSubscriptionListResponse>("/push/subscriptions"),
    retry: false,
  });
}

/**
 * G10 — `PUT /push/subscriptions`.
 *
 * Lỗi cần hiển thị được, không nuốt: `409 PUSH_SUBSCRIPTION_LIMIT` (trần 10 thiết bị,
 * p12 §12.3.2), `403 CONSENT_REQUIRED` (chưa đồng ý `HEALTH_REMINDER_PUSH`),
 * `400 PUSH_SUBSCRIPTION_INVALID` (thiếu cả `fid` lẫn `legacyToken`). `ApiError.code` giữ
 * nguyên mã để UI tra chuỗi dịch.
 */
export function useUpsertPushSubscription(): UseMutationResult<
  PushSubscriptionItem,
  Error,
  UpsertPushSubscriptionPayload
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: UpsertPushSubscriptionPayload) =>
      apiFetch<PushSubscriptionItem>("/push/subscriptions", { method: "PUT", body: JSON.stringify(payload) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.pushSubscriptions() });
    },
  });
}

/** G11 — `DELETE /push/subscriptions/{id}` (204), `revoke_reason = USER_DISABLED`. */
export function useRevokePushSubscription(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (subscriptionId: string) =>
      apiFetch<undefined>(`/push/subscriptions/${subscriptionId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.pushSubscriptions() });
    },
  });
}
