
export type ReminderType = "SCAN_ROUTINE" | "CREDIT_EXPIRY" | "SURVEY_FOLLOWUP";

export type ReminderScheduleKind = "INTERVAL" | "RRULE";

export type ReminderSource = "USER" | "SUGGESTED";

export type ReminderChannel = "PUSH" | "EMAIL" | "IN_APP";

export const REMINDER_CHANNELS = ["PUSH", "EMAIL", "IN_APP"] as const satisfies readonly ReminderChannel[];

export const INTERVAL_DAYS_MIN = 1;
export const INTERVAL_DAYS_MAX = 90;

export interface Reminder {
  id: string;
  catId?: string;
  type: ReminderType;
  scheduleKind: ReminderScheduleKind;
  intervalDays?: number;
  rrule?: string;
  preferredTimeStart?: string;
  preferredTimeEnd?: string;
  timezone?: string;
  nextRunAt?: string;
  lastRunAt?: string;
  lastSatisfiedAt?: string;
  channels?: ReminderChannel[];
  source: ReminderSource;
  active: boolean;
}

export interface ReminderListResponse {
  items: Reminder[];
}

export interface ReminderListFilter {
  catId?: string;
  active?: boolean;
}

export interface CreateReminderPayload {
  catId?: string;
  type: ReminderType;
  scheduleKind: ReminderScheduleKind;
  intervalDays?: number;
  rrule?: string;
  preferredTimeStart?: string;
  preferredTimeEnd?: string;
  timezone?: string;
  channels: ReminderChannel[];
  source?: ReminderSource;
}

export interface PatchReminderPayload {
  scheduleKind?: ReminderScheduleKind;
  intervalDays?: number;
  rrule?: string;
  preferredTimeStart?: string;
  preferredTimeEnd?: string;
  channels?: ReminderChannel[];
  active?: boolean;
}

export const REMINDER_ERROR = {
  NOT_FOUND: "REMINDER_NOT_FOUND",
  LIMIT_REACHED: "REMINDER_LIMIT_REACHED",
  SCHEDULE_INVALID: "REMINDER_SCHEDULE_INVALID",
  CAT_NOT_FOUND: "CAT_NOT_FOUND",
  FEATURE_NOT_IN_PLAN: "FEATURE_NOT_IN_PLAN",
} as const;

export type ReminderErrorCode = (typeof REMINDER_ERROR)[keyof typeof REMINDER_ERROR];
