/** Hằng số dùng chung toàn app. */
export const APP_NAME = "CatCheck";

export const DEFAULT_LOCALE = "vi";
export const SUPPORTED_LOCALES = ["vi", "en"] as const;

// Bug thật đã sửa: tên header trước đây là "X-CSRF-Token" — KHÔNG khớp
// `CookieCsrfTokenRepository` của Spring Security (mặc định đọc header `X-XSRF-TOKEN` đi kèm
// cookie `XSRF-TOKEN`), nên MỌI request ghi (login/register/…) trả 403 dù cookie đã có. Spec
// chốt `X-XSRF-TOKEN` ở p8 §8.3 (bảng header), p9 §9.6.3, p11 §11.1.1 và 02-team-decisions.
export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
export const CSRF_COOKIE_NAME = "XSRF-TOKEN";
export const IDEMPOTENCY_HEADER_NAME = "Idempotency-Key";
