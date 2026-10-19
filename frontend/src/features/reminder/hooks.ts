import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { isApiError } from "@/shared/api";
import { createReminder, deleteReminder, getReminder, listReminders, patchReminder } from "./api";
import { REMINDER_ERROR } from "./types";
import type {
  CreateReminderPayload,
  PatchReminderPayload,
  Reminder,
  ReminderListFilter,
  ReminderListResponse,
} from "./types";

export const reminderKeys = {
  all: ["reminder"] as const,
  lists: () => [...reminderKeys.all, "list"] as const,
  list: (filter: ReminderListFilter) =>
    [...reminderKeys.lists(), filter.catId ?? "all", filter.active ?? "any"] as const,
  details: () => [...reminderKeys.all, "detail"] as const,
  detail: (reminderId: string) => [...reminderKeys.details(), reminderId] as const,
};

const REMINDER_STALE_TIME = 60 * 1000;

export function useReminders(filter: ReminderListFilter = {}, enabled = true): UseQueryResult<ReminderListResponse> {
  return useQuery({
    queryKey: reminderKeys.list(filter),
    queryFn: () => listReminders(filter),
    staleTime: REMINDER_STALE_TIME,
    enabled,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function useReminder(reminderId: string | undefined): UseQueryResult<Reminder> {
  return useQuery({
    queryKey: reminderKeys.detail(reminderId ?? ""),
    queryFn: () => getReminder(reminderId ?? ""),
    enabled: Boolean(reminderId),
    staleTime: REMINDER_STALE_TIME,
  });
}

export function useCreateReminder(): UseMutationResult<Reminder, Error, CreateReminderPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createReminder,
    onSuccess: (reminder) => {
      queryClient.setQueryData(reminderKeys.detail(reminder.id), reminder);
      void queryClient.invalidateQueries({ queryKey: reminderKeys.lists() });
    },
  });
}

export function useUpdateReminder(): UseMutationResult<
  Reminder,
  Error,
  { reminderId: string; patch: PatchReminderPayload }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reminderId, patch }) => patchReminder(reminderId, patch),
    onSuccess: (reminder) => {
      queryClient.setQueryData(reminderKeys.detail(reminder.id), reminder);
      void queryClient.invalidateQueries({ queryKey: reminderKeys.lists() });
    },
  });
}

export function useDeleteReminder(): UseMutationResult<void, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteReminder,
    onSuccess: (_result, reminderId) => {
      queryClient.removeQueries({ queryKey: reminderKeys.detail(reminderId) });
      void queryClient.invalidateQueries({ queryKey: reminderKeys.lists() });
    },
  });
}

export function reminderErrorCode(error: unknown): string | undefined {
  return isApiError(error) ? error.code : undefined;
}

export function isFeatureLocked(error: unknown): boolean {
  if (!isApiError(error)) return false;
  return error.status === 403 && (error.code === REMINDER_ERROR.FEATURE_NOT_IN_PLAN || error.code === undefined);
}
