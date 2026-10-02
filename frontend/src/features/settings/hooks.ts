import { useMutation, useQuery, useQueryClient, type UseMutationResult, type UseQueryResult } from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { NotificationPreferences } from "./types";

/**
 * B11/B12 — tuỳ chọn thông báo.
 *
 * Hợp đồng JSON xem `types.ts`. `apiFetch` là wrapper cục bộ của feature này (`./api`) —
 * eslint `boundaries` cấm feature import feature nên không dùng lại được của auth.
 */

export const NOTIFICATION_PREFERENCES_KEY = ["settings", "notificationPreferences"] as const;

export function useNotificationPreferences(): UseQueryResult<NotificationPreferences> {
  return useQuery({
    queryKey: NOTIFICATION_PREFERENCES_KEY,
    queryFn: () => apiFetch<NotificationPreferences>("/account/notification-preferences"),
    // Endpoint từng là stub ném 403; nếu server chưa hiện thực thì đừng retry vô ích.
    retry: false,
  });
}

export function useUpdateNotificationPreferences(): UseMutationResult<
  NotificationPreferences,
  Error,
  NotificationPreferences
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<NotificationPreferences>("/account/notification-preferences", {
        method: "PUT",
        body: JSON.stringify(payload),
      }),
    onSuccess: (data) => {
      queryClient.setQueryData(NOTIFICATION_PREFERENCES_KEY, data);
    },
  });
}
