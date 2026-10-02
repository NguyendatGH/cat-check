import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import {
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
  IDEMPOTENCY_HEADER_NAME,
} from "@/shared/config/constants";
import type { Cat } from "@/entities/cat";
import type { CatPage, ExportJob, ExportJobPage, ExportRequestPayload } from "./types";

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

/** J1 — tạo job xử lý nền, trả `202` + body job (trạng thái ban đầu `QUEUED`). */
export function requestExport(payload: ExportRequestPayload): Promise<ExportJob> {
  return apiFetch<ExportJob>("/exports", { method: "POST", body: JSON.stringify(payload) });
}

/** J2 — danh sách bản xuất của tôi, phân trang con trỏ. */
export function listExports(cursor?: string | null): Promise<ExportJobPage> {
  const qs = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
  return apiFetch<ExportJobPage>(`/exports${qs}`);
}

/** J3 — trạng thái một job (dùng để poll tới khi `READY`/`FAILED`). */
export function getExportJob(jobId: string): Promise<ExportJob> {
  return apiFetch<ExportJob>(`/exports/${jobId}`);
}

/** J4 — URL tải file PDF. Dùng trực tiếp qua `<a href>` (điều hướng same-origin tự gửi cookie
 * phiên) thay vì fetch + blob — không cần xử lý thêm ở client. */
export function exportDownloadUrl(jobId: string): string {
  return `${BASE_URL}/exports/${jobId}/download`;
}

/**
 * D1 — `GET /cats?status=ACTIVE`, dùng cho bước 1 wizard (chọn mèo). KHÔNG import
 * `features/cat`/`features/scan` (boundaries cấm feature → feature) nên tự gọi lại D1 — xem
 * `types.ts#CatPage` (trùng với `features/scan/types.ts#CatPage`, có chủ đích).
 */
export async function listActiveCats(): Promise<Cat[]> {
  const page = await apiFetch<CatPage<Cat>>("/cats?status=ACTIVE");
  return page.items;
}
