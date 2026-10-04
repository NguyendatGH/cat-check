import type { Middleware } from "openapi-fetch";
import { ApiError } from "../errors";

/**
 * Chuẩn hoá lỗi HTTP thành ApiError. KHÔNG log body response ra console (có thể chứa PII —
 * p17 §17.10b) — chỉ log status + code.
 */
export const errorMiddleware: Middleware = {
  async onResponse({ response }) {
    if (response.ok) {
      return response;
    }

    let code: string | undefined;
    try {
      const cloned = response.clone();
      const body: unknown = await cloned.json();
      // Field là `errorCode`: `GlobalExceptionHandler` của backend gọi
      // `problemDetail.setProperty("errorCode", ...)` và KHÔNG bao giờ set `code`. Bản trước
      // đọc `code` nên `ApiError.code` luôn `undefined`, làm mọi nhánh rẽ theo mã lỗi chết âm
      // thầm. Lỗi này tự che chính nó: mock MSW cũng từng phát `code`.
      if (body && typeof body === "object" && "errorCode" in body && typeof body.errorCode === "string") {
        code = body.errorCode;
      }
    } catch {
      // Body không phải JSON — bỏ qua, dùng status làm thông tin chính.
    }

    throw new ApiError(`Request failed with status ${String(response.status)}`, response.status, code);
  },
};
