import createClient from "openapi-fetch";
import type { paths } from "./schema";
import { csrfMiddleware } from "./middleware/csrf";
import { localeMiddleware } from "./middleware/locale";
import { idempotencyMiddleware } from "./middleware/idempotency";
import { errorMiddleware } from "./middleware/error";

/**
 * Client API DUY NHẤT của app — nơi duy nhất được phép import `openapi-fetch` trực tiếp
 * (rule ESLint no-restricted-imports). Same-origin, session cookie HttpOnly, nên baseUrl
 * tương đối "/api" (proxy qua vite.config.ts lúc dev, cùng origin lúc production).
 */
export const apiClient = createClient<paths>({
  baseUrl: "/api",
  credentials: "include",
});

apiClient.use(csrfMiddleware, localeMiddleware, idempotencyMiddleware, errorMiddleware);
