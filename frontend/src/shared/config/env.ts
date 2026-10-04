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
  /**
   * Cấu hình Firebase Web App. VAPID key MỘT MÌNH không đủ để đăng ký push: p12 §12.3.3 bước 3
   * yêu cầu `initializeApp(...)` trước rồi mới `register(messaging, { vapidKey, swRegistration })`,
   * mà `initializeApp` cần apiKey + projectId + appId + messagingSenderId. Bốn khoá này đều là
   * giá trị PUBLIC của Firebase (nằm trong bundle JS của mọi web app Firebase), không phải secret.
   * Tất cả optional: thiếu thì UI nói thẳng "chưa cấu hình", không có nút chết.
   */
  VITE_FIREBASE_API_KEY: z.string().optional(),
  VITE_FIREBASE_PROJECT_ID: z.string().optional(),
  VITE_FIREBASE_APP_ID: z.string().optional(),
  VITE_FIREBASE_MESSAGING_SENDER_ID: z.string().optional(),
  /** Optional XYZ raster provider; local smoke uses the Figma map fallback when absent. */
  VITE_MAP_TILES_URL: z.string().pipe(z.url()).optional(),
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

/**
 * Cấu hình `initializeApp` — `null` khi thiếu bất kỳ khoá nào.
 *
 * Tách khỏi `isPushEnabled` một cách CỐ Ý: `isPushEnabled` là cờ sản phẩm "đã có VAPID key hay
 * chưa" mà phần còn lại của repo (và brief) đang dựa vào; `firebaseWebConfig` là điều kiện kỹ
 * thuật đủ để SDK khởi tạo được. Hai thứ hỏng theo hai cách khác nhau nên hiện hai câu giải
 * thích khác nhau cho người dùng, thay vì gộp thành một cờ mơ hồ.
 */
export const firebaseWebConfig: {
  apiKey: string;
  projectId: string;
  appId: string;
  messagingSenderId: string;
} | null =
  env.VITE_FIREBASE_API_KEY &&
  env.VITE_FIREBASE_PROJECT_ID &&
  env.VITE_FIREBASE_APP_ID &&
  env.VITE_FIREBASE_MESSAGING_SENDER_ID
    ? {
        apiKey: env.VITE_FIREBASE_API_KEY,
        projectId: env.VITE_FIREBASE_PROJECT_ID,
        appId: env.VITE_FIREBASE_APP_ID,
        messagingSenderId: env.VITE_FIREBASE_MESSAGING_SENDER_ID,
      }
    : null;

/** VAPID public key (p12 §12.3.3 bước 4). `null` khi chưa cấu hình. */
export const firebaseVapidKey: string | null = env.VITE_FIREBASE_VAPID_KEY ?? null;

export const mapTilesUrl: string | null = env.VITE_MAP_TILES_URL ?? null;
