import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { NotificationPreferences } from "./types";


export const NOTIFICATION_PREFERENCES_KEY = ["settings", "notificationPreferences"] as const;

export function useNotificationPreferences(): UseQueryResult<NotificationPreferences> {
  return useQuery({
    queryKey: NOTIFICATION_PREFERENCES_KEY,
    queryFn: () => apiFetch<NotificationPreferences>("/account/notification-preferences"),
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
