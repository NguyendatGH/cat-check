import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";
import type { TrendRange } from "@/features/trends";

/**
 * D13 `GET /cats/{catId}/trends` — endpoint THẬT (trước đây trả 501 nên bản desktop phải
 * dựng bằng hằng `WEB_DEMO_*`).
 *
 * Đặt cục bộ trong `pages/trends/` thay vì `features/trends/`: `features/trends/hooks.ts`
 * hiện tự dựng chuỗi xu hướng từ E2 (`GET /scans`) cho bản mobile vì D13 chưa có — giữ
 * nguyên đường đó để không phá bản mobile đang chạy, bản desktop dùng D13 trực tiếp.
 *
 * `apiFetch` của `features/trends` map `problem.code`, nhưng backend trả tên trường là
 * `errorCode` (xác nhận bằng curl: `{"title":"FEATURE_NOT_IN_PLAN","errorCode":"FEATURE_NOT_IN_PLAN"}`)
 * nên ở đây đọc cả hai — cần `code` chính xác để phân biệt 403 "chưa có gói" với 403 khác.
 */

const BASE_URL = "/api/v1";

/** Mã lỗi khi gói hiện tại không có entitlement `trend` (p5 R5, p8 §8.2.4). */
export const FEATURE_NOT_IN_PLAN = "FEATURE_NOT_IN_PLAN";

export interface CatTrendPoint {
  capturedAt: string;
  phValue: number;
  classification: string;
  confidence: number | null;
  nearBoundary: boolean;
  disputed: boolean;
}

export interface CatTrendStats {
  median: number | null;
  min: number | null;
  max: number | null;
  count: number;
  inRangeCount: number;
  lowConfidenceCount: number;
}

/** Dải pH kèm trong response D13 — cùng nguồn với `entities/ph-bands`, KHÔNG hard-code ngưỡng. */
export interface CatTrendBand {
  code: string;
  minPh: number | null;
  maxPh: number | null;
  severity: string;
  label: string;
  colorToken: string;
  sortOrder: number;
}

export interface CatTrendsResponse {
  range: string;
  from: string;
  to: string;
  points: CatTrendPoint[];
  stats: CatTrendStats;
  bands: CatTrendBand[];
  chartVersions: number[];
}

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

async function fetchCatTrends(catId: string, range: TrendRange): Promise<CatTrendsResponse> {
  const headers = new Headers({ Accept: "application/json" });
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

export const catTrendsKeys = {
  all: ["cat-trends"] as const,
  byRange: (catId: string, range: TrendRange) => [...catTrendsKeys.all, catId, range] as const,
};

export function useCatTrends(
  catId: string | undefined,
  range: TrendRange,
): UseQueryResult<CatTrendsResponse> {
  return useQuery({
    queryKey: catTrendsKeys.byRange(catId ?? "", range),
    queryFn: () => {
      if (!catId) throw new Error("catId is required");
      return fetchCatTrends(catId, range);
    },
    enabled: Boolean(catId),
  });
}
