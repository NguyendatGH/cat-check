import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type { ScanListItem, ScanListPage, ScanSummary } from "@/entities/scan-result";

/** Fetch wrapper cục bộ — cùng quy ước `features/onboarding/api.ts` (base `/api/v1`, chỉ GET). */
const BASE_URL = "/api/v1";
const MAX_PAGES = 10;

interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code?: string;
  /** Backend set đúng field này (`setProperty("errorCode", …)`), KHÔNG phải `code`. */
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
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
      // bỏ qua
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return (await response.json()) as T;
}

/**
 * E2, lặp trang tới khi hết hoặc chạm `MAX_PAGES` (đủ cho 90 ngày ở tần suất quét thực tế —
 * không cần true streaming). Dùng để tự vẽ chuỗi xu hướng thay cho D13 (chưa triển khai).
 */
export async function fetchAllScansInRange(catId: string, from: string, to: string): Promise<ScanListItem[]> {
  const items: ScanListItem[] = [];
  let cursor: string | null = null;
  for (let page = 0; page < MAX_PAGES; page += 1) {
    const qs = new URLSearchParams({ catId, from, to, limit: "50" });
    if (cursor) qs.set("cursor", cursor);
    const result: ScanListPage = await apiFetch<ScanListPage>(`/scans?${qs.toString()}`);
    items.push(...result.items);
    if (!result.hasMore || !result.nextCursor) break;
    cursor = result.nextCursor;
  }
  return items;
}

/** E3 — dùng cho phân bố phân loại + trung vị/khoảng min-max trong kỳ. */
export function fetchScanSummary(catId: string, from: string, to: string): Promise<ScanSummary> {
  const qs = new URLSearchParams({ catId, from, to });
  return apiFetch<ScanSummary>(`/scans/summary?${qs.toString()}`);
}
