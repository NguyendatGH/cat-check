import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";
import type { Cat } from "@/entities/cat";
import type { CatPage, ExportJob, ExportJobPage, ExportRequestPayload } from "./types";

const BASE_URL = "/api/v1";

interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance: string;
  code?: string;
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
      // Body không phải JSON: dùng thông báo lỗi mặc định theo status.
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

export function requestExport(payload: ExportRequestPayload): Promise<ExportJob> {
  // PDF theo ngôn ngữ đang hiển thị, không theo hồ sơ (có thể lệch khi lưu hồ sơ lỗi).
  return apiFetch<ExportJob>("/exports", {
    method: "POST",
    body: JSON.stringify({ locale: currentAcceptLanguage(), ...payload }),
  });
}

export function listExports(cursor?: string | null): Promise<ExportJobPage> {
  const qs = cursor ? `?cursor=${encodeURIComponent(cursor)}` : "";
  return apiFetch<ExportJobPage>(`/exports${qs}`);
}

export function getExportJob(jobId: string): Promise<ExportJob> {
  return apiFetch<ExportJob>(`/exports/${jobId}`);
}

export function exportDownloadUrl(jobId: string): string {
  return `${BASE_URL}/exports/${jobId}/download`;
}

export async function listActiveCats(): Promise<Cat[]> {
  const page = await apiFetch<CatPage<Cat>>("/cats?status=ACTIVE");
  return page.items;
}
