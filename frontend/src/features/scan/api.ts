import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";
import type { Cat } from "@/entities/cat";
import type { ScanResult } from "@/entities/scan-result";
import type { CatPage, SubmitScanMetadata } from "./types";

/**
 * Fetch wrapper tối thiểu cho `features/scan` — cùng quy ước với `features/onboarding/api.ts`
 * (base `/api/v1`, CSRF, Idempotency-Key, ProblemDetail → `ApiError`). W3 nối API thật: thay
 * bằng `apiClient` khi `schema.d.ts` sinh thật, giữ nguyên chữ ký hàm exported từ `hooks.ts`.
 */

const BASE_URL = "/api/v1";

interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code?: string;
  params?: Record<string, unknown>;
  retryAfterSeconds?: number;
  /** Backend set đúng field này (`setProperty("errorCode", …)`), KHÔNG phải `code`. */
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

async function toApiError(response: Response, fallback: string): Promise<ApiError> {
  let problem: ProblemDetail | undefined;
  try {
    problem = (await response.json()) as ProblemDetail;
  } catch {
    // body không phải JSON — bỏ qua, dùng message mặc định
  }
  return new ApiError(problem?.detail ?? fallback, response.status, problem?.errorCode);
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

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    throw await toApiError(response, `Request failed with status ${String(response.status)}`);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * E1 — `POST /scans` multipart: part `metadata` (JSON) + part `image` (binary). Header
 * `Idempotency-Key` PHẢI khớp `metadata.scanRequestId` (BE trả 400 `SCAN_METADATA_INVALID`
 * nếu lệch — `ScanController.submitScan`). Trả `200` cả khi kết quả `INCONCLUSIVE`.
 */
export async function apiSubmitScan(metadata: SubmitScanMetadata, file: File): Promise<ScanResult> {
  const formData = new FormData();
  formData.append("metadata", new Blob([JSON.stringify(metadata)], { type: "application/json" }));
  formData.append("image", file);

  const headers = new Headers({ [IDEMPOTENCY_HEADER_NAME]: metadata.scanRequestId });
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }

  const response = await fetch(`${BASE_URL}/scans`, {
    method: "POST",
    body: formData,
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    throw await toApiError(response, `Submit failed with status ${String(response.status)}`);
  }
  return (await response.json()) as ScanResult;
}

/**
 * D1 — `GET /cats?status=ACTIVE`, dùng cho bước chọn mèo trước khi quét (`/scan/select-cat`).
 * KHÔNG import `features/cat` (boundaries cấm feature → feature) nên gọi lại đúng endpoint D1
 * bằng `apiFetch` cục bộ — xem `types.ts#CatPage`.
 */
export async function listActiveCats(): Promise<Cat[]> {
  const page = await apiFetch<CatPage<Cat>>("/cats?status=ACTIVE");
  return page.items;
}
