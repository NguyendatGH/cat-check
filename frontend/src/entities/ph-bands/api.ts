import { ApiError } from "@/shared/api/errors";

/**
 * Fetch wrapper tối thiểu — cùng quy ước với `features/onboarding/api.ts` (base `/api/v1`,
 * lỗi RFC 9457 → `ApiError`). Cả hai endpoint F1/F5 đều `GET` công khai (không cookie phiên
 * bắt buộc, không CSRF/Idempotency-Key) nên không cần phần header ghi của các feature khác.
 * TODO (W3): thay bằng `shared/api/client` (`apiClient`) khi `schema.d.ts` được sinh thật.
 */
const BASE_URL = "/api/v1";

interface ProblemDetail {
  detail?: string;
  code?: string;
}

export async function phBandsApiFetch<T>(path: string): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    credentials: "include",
    headers: { Accept: "application/json" },
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // body không phải JSON — bỏ qua, dùng message mặc định
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.code,
    );
  }

  return (await response.json()) as T;
}
