import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type {
  CreateReminderPayload,
  PatchReminderPayload,
  Reminder,
  ReminderListFilter,
  ReminderListResponse,
} from "./types";

/**
 * Fetch wrapper cục bộ — cùng quy ước `features/settings/api.ts` (base `/api/v1`,
 * `credentials: include`, CSRF header, lỗi RFC 9457 → `ApiError`).
 *
 * Mỗi feature tự giữ wrapper của mình vì eslint `boundaries` cấm feature import feature;
 * đây là convention sẵn có của repo, không phải trùng lặp ngoài ý muốn. Chỉ
 * `features/auth/api.ts` bootstrap cookie CSRF (`GET /auth/csrf`), các wrapper còn lại —
 * kể cả cái này — chỉ ĐỌC cookie đã có.
 *
 * HAI KHÁC BIỆT CÓ CHỦ Ý so với bản `settings`, cả hai đều do hợp đồng của nhóm I:
 *
 *  1. `Content-Type` chỉ được đặt mặc định khi caller CHƯA đặt. I4 nhận
 *     `application/merge-patch+json`; ghi đè vô điều kiện như bản `settings` sẽ xoá mất
 *     header đó và request PATCH mang sai kiểu nội dung.
 *  2. Mã lỗi đọc từ `errorCode` trước, rồi mới tới `code`/`title`.
 *     `GlobalExceptionHandler` phía server đặt `problemDetail.setProperty("errorCode", …)`
 *     và `setTitle(code)` — KHÔNG có trường `code`. Bản `settings` đọc `problem.code` nên
 *     luôn nhận `undefined`; màn này cần mã chính xác để phân biệt 409 LIMIT_REACHED với
 *     403 FEATURE_NOT_IN_PLAN nên phải đọc đúng tên trường (cùng cách
 *     `pages/trends/catTrendsApi.ts` đã làm).
 */
const BASE_URL = "/api/v1";

const MERGE_PATCH_CONTENT_TYPE = "application/merge-patch+json";

interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  /** Tên trường thật server trả về (xem ghi chú đầu file). */
  errorCode?: string;
  code?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (options.body !== undefined && !headers.has("Content-Type")) {
    headers.set("Content-Type", "application/json");
  }
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  const response = await fetch(`${BASE_URL}${path}`, { ...options, credentials: "include", headers });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // bỏ qua — vẫn ném ApiError với status gốc
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode ?? problem?.code ?? problem?.title,
    );
  }
  // I5 trả 204 không body — `response.json()` sẽ ném nếu gọi vô điều kiện.
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

// ─── Chuyển đổi định dạng giờ ────────────────────────────────────────────────────────────
//
// BẤT ĐỐI XỨNG CÓ THẬT của hợp đồng: request DTO khai `@JsonFormat(pattern = "HH:mm")` nên
// phải GỬI `"08:00"`, trong khi response serialize `LocalTime` mặc định nên NHẬN `"08:00:00"`.
// Hai hàm dưới là ranh giới duy nhất xử lý việc này; UI chỉ làm việc với `HH:mm`.

/** `"08:00:00"` (server trả) → `"08:00"` (giá trị của `<input type="time">`). */
export function apiTimeToInput(value: string | undefined): string {
  return value === undefined ? "" : value.slice(0, 5);
}

/** `"08:00"` (input) → `"08:00"` (server nhận). Chuỗi rỗng ⇒ `undefined` để bỏ khỏi payload. */
export function inputTimeToApi(value: string): string | undefined {
  return value === "" ? undefined : value.slice(0, 5);
}

// ─── I1–I6 ───────────────────────────────────────────────────────────────────────────────

/** I1 `GET /reminders?catId=&active=`. */
export function listReminders(filter: ReminderListFilter = {}): Promise<ReminderListResponse> {
  const search = new URLSearchParams();
  if (filter.catId !== undefined) search.set("catId", filter.catId);
  if (filter.active !== undefined) search.set("active", String(filter.active));
  const query = search.toString();
  return apiFetch<ReminderListResponse>(`/reminders${query === "" ? "" : `?${query}`}`);
}

/** I2 `POST /reminders` → 201. */
export function createReminder(payload: CreateReminderPayload): Promise<Reminder> {
  return apiFetch<Reminder>("/reminders", { method: "POST", body: JSON.stringify(payload) });
}

/** I3 `GET /reminders/{id}`. */
export function getReminder(reminderId: string): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${reminderId}`);
}

/** I4 `PATCH /reminders/{id}` — merge-patch, field vắng mặt = không đổi. */
export function patchReminder(reminderId: string, payload: PatchReminderPayload): Promise<Reminder> {
  return apiFetch<Reminder>(`/reminders/${reminderId}`, {
    method: "PATCH",
    headers: { "Content-Type": MERGE_PATCH_CONTENT_TYPE },
    body: JSON.stringify(payload),
  });
}

/** I5 `DELETE /reminders/{id}` → 204 (xoá mềm). */
export async function deleteReminder(reminderId: string): Promise<void> {
  await apiFetch<undefined>(`/reminders/${reminderId}`, { method: "DELETE" });
}

/**
 * I6 — URL file `.ics` ("Thêm vào lịch"). Trả URL thay vì fetch: đây là GET công khai theo
 * phiên, để trình duyệt tự tải qua `<a download>` thì không phải dựng blob và giữ nguyên
 * `Content-Disposition` server đã đặt.
 */
export function reminderCalendarUrl(reminderId: string): string {
  return `${BASE_URL}/reminders/${reminderId}/calendar.ics`;
}
