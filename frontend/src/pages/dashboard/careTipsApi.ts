import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";

/**
 * F7 `GET /api/v1/care-tips` — nội dung chăm sóc đã công bố (`PublicContentController`).
 * CÔNG KHAI: không cần phiên, không cần CSRF (chỉ GET) ⇒ khách chưa đăng nhập vẫn đọc được.
 *
 * Đặt cục bộ trong `pages/dashboard/` theo đúng tiền lệ `pages/legal/api.ts` và
 * `pages/trends/catTrendsApi.ts`: chỉ Trang chủ dùng, chưa đủ lý do mở một `features/content`.
 *
 * Locale do server quyết theo header `Accept-Language` (p8 §8.1.7) — client KHÔNG gửi tham số
 * `locale` riêng để tránh hai nguồn ngôn ngữ lệch nhau.
 */

const BASE_URL = "/api/v1";

/** `CareTipSummaryResponse` — KHÔNG có ảnh minh hoạ và KHÔNG có `bodyMd` (chỉ F8 mới có). */
export interface CareTipSummary {
  id: string;
  slug: string;
  locale: string;
  /** `ARTICLE` | `TIP` | … theo `CareTipKind` của backend. */
  kind: string;
  /** `CareTipCategory` của backend. */
  category: string;
  title: string;
  summary: string | null;
  tags: string[];
  publishedAt: string | null;
}

interface CareTipPage {
  items: CareTipSummary[];
  limit: number;
  hasMore: boolean;
}

interface ProblemDetail {
  detail?: string;
  errorCode?: string;
}

async function fetchCareTips(limit: number): Promise<CareTipSummary[]> {
  const response = await fetch(`${BASE_URL}/care-tips?limit=${String(limit)}`, {
    credentials: "include",
    headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() },
  });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // body không phải JSON — giữ status làm thông tin duy nhất
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return ((await response.json()) as CareTipPage).items;
}

export const careTipKeys = {
  all: ["care-tips"] as const,
  list: (limit: number) => [...careTipKeys.all, limit] as const,
};

export function useCareTips(limit = 3): UseQueryResult<CareTipSummary[]> {
  return useQuery({
    queryKey: careTipKeys.list(limit),
    queryFn: () => fetchCareTips(limit),
    staleTime: 10 * 60 * 1000,
  });
}
