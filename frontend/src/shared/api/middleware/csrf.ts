import type { Middleware } from "openapi-fetch";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";

function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${name}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

/**
 * Mô hình phiên: session cookie HttpOnly + CSRF token (KHÔNG có JWT/refresh token ở FE).
 * Đính header CSRF từ cookie double-submit cho mọi request thay đổi state.
 *
 * ⚠️ CÒN THIẾU so với p9 §9.6.3: khi cookie chưa có (tab mở lâu / lần tải đầu) phải gọi
 * `GET /api/v1/auth/csrf` một lần rồi thử lại đúng một lần. Hiện middleware chỉ bỏ qua
 * header → request sẽ 403. Chưa nổi lên vì `apiClient` (openapi-fetch) chưa có call site
 * thật; mọi request đang đi qua `apiFetch` của từng feature, nơi đã có `ensureCsrfCookie()`.
 * Phải bổ sung TRƯỚC khi W3 chuyển các feature sang `apiClient` (sau `npm run api:gen`).
 */
export const csrfMiddleware: Middleware = {
  onRequest({ request }) {
    if (SAFE_METHODS.has(request.method)) {
      return request;
    }
    const token = readCookie(CSRF_COOKIE_NAME);
    if (token) {
      request.headers.set(CSRF_HEADER_NAME, token);
    }
    return request;
  },
};
