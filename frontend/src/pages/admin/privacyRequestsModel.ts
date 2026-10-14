import type { AdminDsarRequest, AdminOffsetPage } from "@/features/admin";

// Khớp enum backend: DsarRequestType, DsarStatus, DsarChannel (com.catcheck.privacy.domain).
export const REQUEST_TYPES = [
  "ACCESS_EXPORT",
  "RECTIFY",
  "ERASE",
  "RESTRICT",
  "OBJECT",
  "WITHDRAW_CONSENT",
  "PROTECTION_MEASURE",
  "COMPLAINT",
] as const;

export const STATUSES = ["RECEIVED", "IDENTITY_PENDING", "IN_PROGRESS", "EXTENDED", "COMPLETED", "REJECTED"] as const;
// SELF_SERVICE bị backend từ chối khi admin tạo hộ (AdminPrivacyController.parseChannel).
export const CREATE_CHANNELS = ["WEB_FORM", "EMAIL", "POST"] as const;
export const TRANSITIONS = ["ACK", "ASSIGN", "EXTEND", "COMPLETE", "REJECT"] as const;
// AdminDsarService: ADMIN_SUPPORT chỉ được ACK/ASSIGN; COMPLETE/REJECT không hoàn tác được nên cần xác nhận riêng.
export const SUPPORT_TRANSITIONS: readonly string[] = ["ACK", "ASSIGN"];
export const IRREVERSIBLE_TRANSITIONS: readonly string[] = ["COMPLETE", "REJECT"];
export const CLOSED_STATUSES: readonly string[] = ["COMPLETED", "REJECTED"];

// Thời hạn hoàn tất theo DsarRequest.fulfilDaysFor (ngày lịch, tính từ lúc tiếp nhận).
export const FULFIL_DAYS: Record<(typeof REQUEST_TYPES)[number], number> = {
  ACCESS_EXPORT: 10,
  RECTIFY: 10,
  RESTRICT: 15,
  OBJECT: 15,
  WITHDRAW_CONSENT: 15,
  PROTECTION_MEASURE: 15,
  COMPLAINT: 15,
  ERASE: 20,
};

export type TagTone = "neutral" | "success" | "warning" | "danger" | "info";

export function statusTone(status: string): TagTone {
  switch (status) {
    case "IDENTITY_PENDING":
    case "EXTENDED":
      return "warning";
    case "IN_PROGRESS":
      return "info";
    case "COMPLETED":
      return "success";
    case "REJECTED":
      return "danger";
    default:
      return "neutral";
  }
}

export function typeTone(requestType: string): TagTone {
  if (requestType === "ERASE") return "danger";
  if (requestType === "ACCESS_EXPORT") return "info";
  return "neutral";
}

export function dueAtOf(item: Pick<AdminDsarRequest, "extendedTo" | "fulfilDueAt">): string {
  return item.extendedTo ?? item.fulfilDueAt;
}

export function isClosed(status: string): boolean {
  return CLOSED_STATUSES.includes(status);
}

export function isOverdue(item: AdminDsarRequest, now: number): boolean {
  return !isClosed(item.status) && Date.parse(dueAtOf(item)) < now;
}

export type Countdown =
  | { kind: "daysLeft" | "hoursLeft" | "daysOverdue" | "hoursOverdue"; count: number }
  | { kind: "none" };

const HOUR_MS = 3_600_000;
const DAY_MS = 24 * HOUR_MS;

/** Đếm ngược tới hạn cho yêu cầu còn mở; yêu cầu đã đóng hoặc ngày không hợp lệ trả "none". */
export function countdownOf(item: AdminDsarRequest, now: number): Countdown {
  if (isClosed(item.status)) return { kind: "none" };
  const due = Date.parse(dueAtOf(item));
  if (Number.isNaN(due)) return { kind: "none" };
  const diff = due - now;
  const abs = Math.abs(diff);
  if (abs < DAY_MS) {
    const hours = Math.max(1, Math.floor(abs / HOUR_MS));
    return { kind: diff >= 0 ? "hoursLeft" : "hoursOverdue", count: hours };
  }
  const days = Math.floor(abs / DAY_MS);
  return { kind: diff >= 0 ? "daysLeft" : "daysOverdue", count: days };
}

/**
 * Backend DSAR trả số trang ở trường `page` (AdminPrivacyController.PageResponse), không phải `number`
 * như AdminOffsetPage khai báo — đọc thẳng `number` cho ra undefined + 1 = NaN ("Trang NaN/1").
 */
export function currentPageOf(data: AdminOffsetPage<unknown>, fallback: number): number {
  const raw = data as unknown as { number?: unknown; page?: unknown };
  if (typeof raw.number === "number" && Number.isFinite(raw.number)) return raw.number;
  if (typeof raw.page === "number" && Number.isFinite(raw.page)) return raw.page;
  return fallback;
}

/**
 * Thao tác hiển thị cho một yêu cầu. Ẩn những thao tác backend chắc chắn từ chối:
 * - EXTEND khi đã gia hạn (chỉ được 1 lần);
 * - COMPLETE cho ERASE (cần DPO duyệt xoá) và ACCESS_EXPORT (tự hoàn tất khi tệp xuất sẵn sàng).
 */
export function allowedActionsFor(
  request: Pick<AdminDsarRequest, "requestType" | "extendedTo">,
  canAll: boolean,
): string[] {
  const base: readonly string[] = canAll ? TRANSITIONS : TRANSITIONS.filter((v) => SUPPORT_TRANSITIONS.includes(v));
  return base.filter((value) => {
    if (value === "EXTEND") return request.extendedTo === null;
    if (value === "COMPLETE") return request.requestType !== "ERASE" && request.requestType !== "ACCESS_EXPORT";
    return true;
  });
}
