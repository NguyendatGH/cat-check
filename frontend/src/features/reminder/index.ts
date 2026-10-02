// features/reminder — lịch nhắc quét cát định kỳ (p8 §8.4.9, nhóm I: I1–I6).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: code nằm ở các file root của feature (api/hooks/types, không thư mục con)
// theo đúng khuôn mẫu `features/cat` và `features/settings`.
export {
  apiFetch,
  apiTimeToInput,
  createReminder,
  deleteReminder,
  getReminder,
  inputTimeToApi,
  listReminders,
  patchReminder,
  reminderCalendarUrl,
} from "./api";
export {
  isFeatureLocked,
  reminderErrorCode,
  reminderKeys,
  useCreateReminder,
  useDeleteReminder,
  useReminder,
  useReminders,
  useUpdateReminder,
} from "./hooks";
export { INTERVAL_DAYS_MAX, INTERVAL_DAYS_MIN, REMINDER_CHANNELS, REMINDER_ERROR } from "./types";
export type {
  CreateReminderPayload,
  PatchReminderPayload,
  Reminder,
  ReminderChannel,
  ReminderErrorCode,
  ReminderListFilter,
  ReminderListResponse,
  ReminderScheduleKind,
  ReminderSource,
  ReminderType,
} from "./types";
