import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type { CatTrendsResponse, TrendRange } from "./types";

/**
 * D13 `GET /cats/{catId}/trends` — NGUỒN DUY NHẤT của chuỗi xu hướng pH (p8 §8.9 dòng 1186).
 *
 * Trước W1-E có HAI đường: file này tự dựng chuỗi từ E2 `GET /scans` + E3
 * `GET /scans/summary` (viết khi D13 còn là stub 501), còn `pages/trends/catTrendsApi.ts`
 * gọi D13 thật cho bản desktop. Hai đường cho ra số khác nhau trên cùng một bé — Trang chủ
 * (đường tự tổng hợp) và màn Xu hướng desktop (D13) lệch nhau — nên đường tự tổng hợp đã bị
 * xoá hẳn, mọi nơi dùng chung hàm dưới đây.
 *
 * `ApiError.code` đọc từ `errorCode` TRƯỚC rồi mới tới `code`: `GlobalExceptionHandler` đặt
 * `setProperty("errorCode", …)`, cần đúng mã để phân biệt 403 "chưa có gói" với 403 khác.
 */

const BASE_URL = "/api/v1";

/** Mã lỗi khi gói hiện tại không có entitlement `trend` (p5 R5, p8 §8.2.4). */
export const FEATURE_NOT_IN_PLAN = "FEATURE_NOT_IN_PLAN";

interface ProblemDetail {
  detail?: string;
  status?: number;
  code?: string;
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

export async function fetchCatTrends(catId: string, range: TrendRange): Promise<CatTrendsResponse> {
  const headers = new Headers({ Accept: "application/json", "Accept-Language": currentAcceptLanguage() });
  const csrf = readCsrfToken();
  if (csrf) headers.set(CSRF_HEADER_NAME, csrf);

  const response = await fetch(`${BASE_URL}/cats/${catId}/trends?range=${range}`, {
    credentials: "include",
    headers,
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // body rỗng/không phải JSON — giữ undefined, dùng status làm thông tin duy nhất.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode ?? problem?.code,
    );
  }
  return (await response.json()) as CatTrendsResponse;
}
