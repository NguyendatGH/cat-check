import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";


const BASE_URL = "/api/v1";

interface ProblemDetail {
  detail?: string;
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
  if (options.body !== undefined && options.body !== null) {
    headers.set("Content-Type", "application/json; charset=utf-8");
  }
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  const method = options.method ?? "GET";
  if (method !== "GET") {
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
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export async function downloadPrivacyExport(publicRef: string, token: string): Promise<Blob> {
  const headers = new Headers({ Accept: "application/zip", "Accept-Language": currentAcceptLanguage(), "X-DSAR-Download-Token": token });
  const response = await fetch(`${BASE_URL}/privacy/export/${encodeURIComponent(publicRef)}/download`, {
    credentials: "include", headers,
  });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try { problem = (await response.json()) as ProblemDetail; } catch { /* Body không phải JSON: dùng thông báo lỗi mặc định theo status. */ }
    throw new ApiError(problem?.detail ?? `Request failed with status ${String(response.status)}`, response.status, problem?.errorCode);
  }
  return response.blob();
}
