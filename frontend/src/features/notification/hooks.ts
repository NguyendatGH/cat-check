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


const INBOX_PAGE_SIZE = 20;

export const notificationKeys = {
  all: ["notification"] as const,
  inbox: () => [...notificationKeys.all, "inbox"] as const,
  unreadCount: () => [...notificationKeys.all, "unread-count"] as const,
  pushSubscriptions: () => [...notificationKeys.all, "push-subscriptions"] as const,
};

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
      lastPage.page.hasMore ? (lastPage.page.nextCursor ?? undefined) : undefined,
  });
}

export function useUnreadNotificationCount(enabled = true): UseQueryResult<UnreadCountResponse> {
  return useQuery({
    enabled,
    queryKey: notificationKeys.unreadCount(),
    queryFn: () => apiFetch<UnreadCountResponse>("/notifications/unread-count"),
    staleTime: 60 * 1000,
    retry: false,
  });
}

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

export function useMarkAllNotificationsRead(): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/notifications/read-all", { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: notificationKeys.all });
    },
  });
}

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

export function usePushSubscriptions(enabled = true): UseQueryResult<PushSubscriptionListResponse> {
  return useQuery({
    enabled,
    queryKey: notificationKeys.pushSubscriptions(),
    queryFn: () => apiFetch<PushSubscriptionListResponse>("/push/subscriptions"),
    retry: false,
  });
}

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
