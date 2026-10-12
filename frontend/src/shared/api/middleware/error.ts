import type { Middleware } from "openapi-fetch";
import { ApiError } from "../errors";

export const errorMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.ok) {
      return response;
    }

    let code: string | undefined;
    try {
      const cloned = response.clone();
      const body: unknown = await cloned.json();
      if (body && typeof body === "object" && "errorCode" in body && typeof body.errorCode === "string") {
        code = body.errorCode;
      }
    } catch {
    }

    throw new ApiError(`Request failed with status ${String(response.status)}`, response.status, code);
  },
};
