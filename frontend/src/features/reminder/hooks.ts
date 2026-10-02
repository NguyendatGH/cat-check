import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { isApiError } from "@/shared/api";
import {
  createReminder,
  deleteReminder,
  getReminder,
  listReminders,
  patchReminder,
} from "./api";
import { REMINDER_ERROR } from "./types";
import type {
  CreateReminderPayload,
  PatchReminderPayload,
  Reminder,
  ReminderListFilter,
  ReminderListResponse,
} from "./types";

/**
 * Nhóm I — lịch nhắc theo dõi. Query-key factory theo cùng khuôn `features/cat/hooks.ts`
 * (`catKeys`): gốc là tên domain, nhánh `list`/`detail` để invalidate theo cụm.
 *
 * `apiFetch` là wrapper cục bộ của feature này (`./api`) — eslint `boundaries` cấm feature
 * import feature nên không dùng lại được của `settings`/`cat`.
 */
export const reminderKeys = {
  all: ["reminder"] as const,
  lists: () => [...reminderKeys.all, "list"] as const,
  list: (filter: ReminderListFilter) =>
    [...reminderKeys.lists(), filter.catId ?? "all", filter.active ?? "any"] as const,
  details: () => [...reminderKeys.all, "detail"] as const,
  detail: (reminderId: string) => [...reminderKeys.details(), reminderId] as const,
};

/** Lịch nhắc đổi chậm (tối đa vài lần/ngày) nhưng `nextRunAt` là mốc thời gian — 60s là đủ. */
const REMINDER_STALE_TIME = 60 * 1000;

/** I1 — danh sách. Truyền `enabled: false` khi chưa biết `catId` của bộ lọc. */
export function useReminders(
  filter: ReminderListFilter = {},
  enabled = true,
): UseQueryResult<ReminderListResponse> {
  return useQuery({
    queryKey: reminderKeys.list(filter),
    queryFn: () => listReminders(filter),
    staleTime: REMINDER_STALE_TIME,
    enabled,
  });
}

/** I3 — chi tiết. */
export function useReminder(reminderId: string | undefined): UseQueryResult<Reminder> {
  return useQuery({
    queryKey: reminderKeys.detail(reminderId ?? ""),
    queryFn: () => getReminder(reminderId ?? ""),
    enabled: Boolean(reminderId),
    staleTime: REMINDER_STALE_TIME,
  });
}

/** I2 — tạo. Invalidate CẢ CỤM list vì key list có chiều `catId`/`active` không đoán trước được. */
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

/**
 * I4 — sửa / bật / tắt. Merge-patch: chỉ truyền field muốn đổi.
 *
 * `reminderId` nhận qua biến `mutate` chứ không bind ở tham số hook — màn danh sách có N thẻ,
 * mỗi thẻ một công tắc bật/tắt, dùng CHUNG một hook ở cấp trang thay vì khởi tạo N hook.
 */
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

/** I5 — xoá mềm. */
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

/**
 * Mã lỗi nghiệp vụ của một `error` bất kỳ, hoặc `undefined` nếu không phải lỗi API có mã.
 * Dùng để chọn trạng thái hiển thị (409 → "sửa lịch hiện có", 403 → upsell gói…).
 */
export function reminderErrorCode(error: unknown): string | undefined {
  return isApiError(error) ? error.code : undefined;
}

/**
 * `true` khi lỗi là 403 do gói không có tính năng lịch nhắc.
 *
 * Mọi 403 đều được coi là "khoá gói": GET không dính CSRF và route đã qua `RequireAuth`,
 * nên 403 duy nhất còn lại ở nhóm I là `FEATURE_NOT_IN_PLAN` — coi rộng như vậy để không
 * rơi vào màn lỗi trắng nếu hình dạng body đổi (cùng lập luận `pages/trends/CatTrendsPage`).
 */
export function isFeatureLocked(error: unknown): boolean {
  if (!isApiError(error)) return false;
  return (
    error.status === 403 &&
    (error.code === REMINDER_ERROR.FEATURE_NOT_IN_PLAN || error.code === undefined)
  );
}
