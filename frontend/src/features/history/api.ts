import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import {
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
  IDEMPOTENCY_HEADER_NAME,
} from "@/shared/config/constants";
import type { Cat } from "@/entities/cat";
import type { ScanListPage, ScanSummary } from "@/entities/scan-result";
import { ABNORMAL_CLASSIFICATIONS, type HistoryFilterParams } from "./types";

/** Fetch wrapper cục bộ — cùng quy ước `features/onboarding/api.ts` (base `/api/v1`). */
const BASE_URL = "/api/v1";

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
  if (options.body && !(options.body instanceof FormData)) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  if (options.method && options.method !== "GET") {
    headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
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
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

function buildQuery(params: HistoryFilterParams & { limit: number }): string {
  const qs = new URLSearchParams();
  if (params.catId) qs.set("catId", params.catId);
  if (params.filter === "ABNORMAL") {
    ABNORMAL_CLASSIFICATIONS.forEach((c) => {
      qs.append("classification", c);
    });
  }
  if (params.filter === "DISPUTED") qs.set("disputed", "true");
  if (params.cursor) qs.set("cursor", params.cursor);
  qs.set("limit", String(params.limit));
  return qs.toString();
}

/** E2 — lịch sử quét, phân trang con trỏ. */
export function fetchScanHistory(params: HistoryFilterParams & { limit: number }): Promise<ScanListPage> {
  return apiFetch<ScanListPage>(`/scans?${buildQuery(params)}`);
}

/** E3 — thống kê quét trong khoảng thời gian (dùng cho phổ pH tháng hiện tại). */
export function fetchScanSummary(params: { catId?: string; from?: string; to?: string }): Promise<ScanSummary> {
  const qs = new URLSearchParams();
  if (params.catId) qs.set("catId", params.catId);
  if (params.from) qs.set("from", params.from);
  if (params.to) qs.set("to", params.to);
  const suffix = qs.toString();
  return apiFetch<ScanSummary>(`/scans/summary${suffix ? `?${suffix}` : ""}`);
}

/** D3 — `GET /cats/{catId}` rút gọn, chỉ để hiển thị tên mèo ở header lịch sử. KHÔNG import
 * `features/cat` (boundaries cấm feature → feature) nên tự gọi lại D3. */
export function fetchCat(catId: string): Promise<Cat> {
  return apiFetch<Cat>(`/cats/${catId}`);
}
