import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

/**
 * Fetch wrapper tối thiểu cho onboarding — dùng thay `apiClient` (openapi-fetch) vì
 * `shared/api/schema.d.ts` đang là placeholder rỗng (chưa chạy `api:gen`), nên
 * `apiClient.GET("/cats")` sẽ lỗi type. W3 nối API thật: thay toàn bộ `apiFetch`
 * bằng `apiClient` + type sinh từ OpenAPI, giữ nguyên chữ ký hàm.
 *
 * Quy ước giống shared/api/upload.ts: base `/api/v1`, credentials include, CSRF
 * header, Idempotency-Key với mọi method khác GET, lỗi RFC 9457 → `ApiError`.
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
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // body không phải JSON — bỏ qua, dùng message mặc định
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

/** Upload multipart (ảnh đại diện mèo) — `PUT /cats/{catId}/avatar` (p8 D10). */
export async function apiUploadAvatar(path: string, file: File): Promise<{ avatarUrl: string }> {
  const formData = new FormData();
  formData.append("file", file);

  const headers = new Headers();
  const csrf = readCsrfToken();
  if (csrf) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());

  const response = await fetch(`${BASE_URL}${path}`, {
    method: "PUT",
    body: formData,
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // bỏ qua
    }
    throw new ApiError(
      problem?.detail ?? `Upload failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }

  return (await response.json()) as { avatarUrl: string };
}
