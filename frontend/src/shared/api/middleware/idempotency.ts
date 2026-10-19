import type { Middleware } from "openapi-fetch";
import { IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

const MUTATING_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

export const idempotencyMiddleware: Middleware = {
  onRequest({ request }) {
    if (MUTATING_METHODS.has(request.method) && !request.headers.has(IDEMPOTENCY_HEADER_NAME)) {
      request.headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
    }
    return request;
  },
};
