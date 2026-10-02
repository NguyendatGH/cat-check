import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

/**
 * Fetch wrapper cục bộ cho module privacy — cùng quy ước với `features/settings/api.ts`
 * (base `/api/v1`, `credentials: include`, CSRF header, ProblemDetail RFC 9457 → `ApiError`).
 *
 * Mỗi feature tự giữ wrapper của mình vì eslint `boundaries` cấm feature import feature;
 * đây là convention sẵn có của repo, không phải trùng lặp ngoài ý muốn.
 *
 * ⚠ Mã lỗi nằm ở thuộc tính **`errorCode`** của ProblemDetail
 * (`GlobalExceptionHandler#setProperty("errorCode", errorCode.code())`) và giá trị là
 * **tên enum** (`ErrorCode#code()` trả `name()`), ví dụ `DSAR_IDENTITY_VERIFICATION_REQUIRED`
 * — không phải slug `dsar-identity-verification-required` (slug chỉ dùng cho `type` URI).
 */

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
      // body không phải JSON — vẫn ném ApiError với status gốc
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
