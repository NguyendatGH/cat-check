import createClient from "openapi-fetch";
import type { paths } from "./schema";
import { csrfMiddleware } from "./middleware/csrf";
import { localeMiddleware } from "./middleware/locale";
import { idempotencyMiddleware } from "./middleware/idempotency";
import { errorMiddleware } from "./middleware/error";

export const apiClient = createClient<paths>({
  baseUrl: "/api",
  credentials: "include",
});

apiClient.use(csrfMiddleware, localeMiddleware, idempotencyMiddleware, errorMiddleware);
