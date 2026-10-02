import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import {
  CSRF_COOKIE_NAME,
  CSRF_HEADER_NAME,
  IDEMPOTENCY_HEADER_NAME,
} from "@/shared/config/constants";

/**
 * Fetch wrapper tối thiểu cho auth — cùng quy ước với `features/onboarding/api.ts`
 * (base `/api/v1`, `credentials: include`, CSRF header, `Idempotency-Key` cho method khác
 * GET, lỗi RFC 9457 -> `ApiError`). `shared/api/schema.d.ts` vẫn là placeholder rỗng nên
 * chưa dùng `apiClient` (openapi-fetch) được — W3 nối API thật thì thay `apiFetch` bằng
 * `apiClient` + type sinh từ OpenAPI, giữ nguyên chữ ký hàm ở `hooks.ts`.
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

/**
 * `ApiError` (shared/api/errors.ts) chỉ giữ `message`/`status`/`code` — không có chỗ cho
 * `params` của ProblemDetail (`attemptsLeft`, `retryAfterSeconds`...). Mở rộng cục bộ ở
 * đây để giữ nguyên các tham số đó cho UI (đếm ngược resend OTP, số lần thử còn lại).
 */
export class AuthApiError extends ApiError {
  readonly params: Record<string, unknown> | undefined;

  constructor(message: string, status: number, code?: string, params?: Record<string, unknown>) {
    super(message, status, code);
    this.name = "AuthApiError";
    this.params = params;
  }
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

/** Ngưỡng cần gọi lại `GET /auth/csrf` một lần trước khi retry (p9 §9.6.3). */
async function ensureCsrfCookie(): Promise<void> {
  if (readCsrfToken()) return;
  try {
    await fetch(`${BASE_URL}/auth/csrf`, { credentials: "include" });
  } catch {
    // Bỏ qua — request gốc vẫn thử, lỗi CSRF thật (nếu có) sẽ nổi lên ở đó.
  }
}

async function parseProblem(response: Response): Promise<ProblemDetail | undefined> {
  try {
    return (await response.json()) as ProblemDetail;
  } catch {
    return undefined;
  }
}

export async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = options.method ?? "GET";
  if (method !== "GET") {
    await ensureCsrfCookie();
  }

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
  if (method !== "GET") {
    headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...options,
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    const problem = await parseProblem(response);
    const params =
      problem?.retryAfterSeconds !== undefined
        ? { ...problem.params, retryAfterSeconds: problem.retryAfterSeconds }
        : problem?.params;
    throw new AuthApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
      params,
    );
  }

  if (response.status === 204) return undefined as T;
  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

/** Đọc tham số ProblemDetail (`attemptsLeft`, `retryAfterSeconds`...) từ một lỗi API. */
export function readErrorParams(error: unknown): Record<string, unknown> | undefined {
  return error instanceof AuthApiError ? error.params : undefined;
}
