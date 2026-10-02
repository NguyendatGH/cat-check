import { z } from "zod";

/**
 * DUY NHẤT file được phép đọc `import.meta.env` trực tiếp (rule ESLint no-restricted-syntax
 * cấm `import.meta.env` ở nơi khác). Validate bằng zod, fail-fast nếu thiếu/sai kiểu.
 *
 * Mô hình phiên: same-origin, session cookie HttpOnly + CSRF token — KHÔNG có JWT/refresh
 * token ở FE, nên không cần VITE_API_BASE_URL khác origin; /api được proxy qua vite.config.ts
 * lúc dev và cùng origin lúc production (VPS + Docker Compose, xem frontend/README.md).
 */
const envSchema = z.object({
  MODE: z.enum(["development", "production", "test"]),
  DEV: z.boolean(),
  PROD: z.boolean(),
  /** Bật khi có Firebase project + VAPID key thật — mặc định tắt (Y4: push tắt tới khi có VAPID key). */
  VITE_FIREBASE_VAPID_KEY: z.string().optional(),
});

const parsed = envSchema.safeParse(import.meta.env);

if (!parsed.success) {
  // Fail fast — sai cấu hình env phải chặn app khởi động ngay, không âm thầm chạy sai.
  // zod v4: `ZodError.flatten()` đã deprecated, dùng `z.treeifyError()`.
  console.error("Cấu hình biến môi trường không hợp lệ:", z.treeifyError(parsed.error));
  throw new Error("Invalid environment configuration — see console for details.");
}

export const env = parsed.data;

export const isPushEnabled = Boolean(env.VITE_FIREBASE_VAPID_KEY);
