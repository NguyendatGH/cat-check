/**
 * Hợp đồng JSON nhóm I — Lịch nhắc theo dõi (p8 §8.4.9, endpoint I1–I6).
 *
 * ⚠ Server bật `default-property-inclusion: non_null`: MỌI field null bị BỎ HẲN khỏi JSON,
 * không trả về `null`. Lịch đang tắt thì response KHÔNG CÓ khoá `nextRunAt` (chứ không phải
 * `"nextRunAt": null`). Vì vậy mọi field có thể vắng đều khai `?:` chứ không phải `| null`,
 * và UI không bao giờ được giả định field đó tồn tại.
 */

/** `reminder.type` — chỉ `SCAN_ROUTINE` do người dùng tự tạo; 2 loại còn lại do hệ thống sinh. */
export type ReminderType = "SCAN_ROUTINE" | "CREDIT_EXPIRY" | "SURVEY_FOLLOWUP";

export type ReminderScheduleKind = "INTERVAL" | "RRULE";

export type ReminderSource = "USER" | "SUGGESTED";

/**
 * Kênh nhắc Phase 1 — backend CHỈ nhận đúng 3 giá trị này.
 *
 * Mockup `09` còn vẽ "Rung nhẹ trên Đồng hồ thông minh" và "Tin nhắn SMS dự phòng": KHÔNG có
 * trong enum của server (gửi lên sẽ bị `REMINDER_SCHEDULE_INVALID`), nên UI bỏ hẳn 2 kênh đó.
 */
export type ReminderChannel = "PUSH" | "EMAIL" | "IN_APP";

export const REMINDER_CHANNELS = ["PUSH", "EMAIL", "IN_APP"] as const satisfies readonly ReminderChannel[];

/** Ràng buộc server kiểm (`@Min(1) @Max(90)` trên `intervalDays`). */
export const INTERVAL_DAYS_MIN = 1;
export const INTERVAL_DAYS_MAX = 90;

/** I1/I2/I3/I4 — một lịch nhắc trả về client. */
export interface Reminder {
  id: string;
  /** Bắt buộc với `SCAN_ROUTINE`; lịch cấp tài khoản (`CREDIT_EXPIRY`) không gắn mèo nên vắng. */
  catId?: string;
  type: ReminderType;
  scheduleKind: ReminderScheduleKind;
  /** Có khi `scheduleKind === "INTERVAL"`. */
  intervalDays?: number;
  /** Có khi `scheduleKind === "RRULE"` (RFC 5545). */
  rrule?: string;
  /** `HH:mm:ss` — LocalTime mặc định của Jackson, KHÁC định dạng `HH:mm` lúc gửi lên. */
  preferredTimeStart?: string;
  preferredTimeEnd?: string;
  timezone?: string;
  /** ISO-8601 UTC. VẮNG khi lịch đang tắt — không được coi là luôn có. */
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

/** Bộ lọc của I1 — cả hai tham số đều tuỳ chọn. */
export interface ReminderListFilter {
  catId?: string;
  active?: boolean;
}

/** I2 `POST /reminders`. `preferredTime*` phải ở định dạng `HH:mm` (`@JsonFormat` của server). */
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

/**
 * I4 `PATCH /reminders/{id}` — merge-patch RFC 7396: field VẮNG MẶT nghĩa là "không đụng tới".
 * Vì vậy mọi field đều optional và hàm gọi chỉ đặt đúng thứ muốn đổi (bật/tắt riêng lẻ chỉ
 * cần `{ active }`).
 */
export interface PatchReminderPayload {
  scheduleKind?: ReminderScheduleKind;
  intervalDays?: number;
  rrule?: string;
  preferredTimeStart?: string;
  preferredTimeEnd?: string;
  channels?: ReminderChannel[];
  active?: boolean;
}

/**
 * Mã lỗi nghiệp vụ của module (`ReminderErrorCode` phía server). Đọc từ thuộc tính `errorCode`
 * của ProblemDetail — xem ghi chú trong `api.ts`.
 */
export const REMINDER_ERROR = {
  /** 404 — không tồn tại HOẶC không thuộc user (p8 §8.2.5: không trả 403 để tránh lộ tồn tại). */
  NOT_FOUND: "REMINDER_NOT_FOUND",
  /** 409 — mèo này ĐÃ có một lịch cùng `type` đang bật (partial unique index ở DB). */
  LIMIT_REACHED: "REMINDER_LIMIT_REACHED",
  /** 400 — payload lịch không hợp lệ (thiếu `intervalDays`, điền cả rrule lẫn interval, kênh lạ…). */
  SCHEDULE_INVALID: "REMINDER_SCHEDULE_INVALID",
  /** 404 — `catId` không tồn tại hoặc không thuộc user. */
  CAT_NOT_FOUND: "CAT_NOT_FOUND",
  /** 403 — gói hiện tại không có tính năng lịch nhắc. */
  FEATURE_NOT_IN_PLAN: "FEATURE_NOT_IN_PLAN",
} as const;

export type ReminderErrorCode = (typeof REMINDER_ERROR)[keyof typeof REMINDER_ERROR];
