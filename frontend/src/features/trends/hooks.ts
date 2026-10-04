import { useMemo } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { fetchCatTrends } from "./api";
import type { CatTrendPoint, CatTrendsResponse, DistributionBucket, TrendPoint, TrendRange } from "./types";

/**
 * Hooks xu hướng pH. MỘT nguồn duy nhất: D13 `GET /cats/{catId}/trends`.
 *
 * `useTrendSeries`/`useTrendSummary` cũ (tự tổng hợp từ `GET /scans` + `GET /scans/summary`)
 * đã bị xoá — chúng tồn tại vì D13 từng là stub 501, và khi D13 chạy thật thì hai đường cho
 * số khác nhau giữa Trang chủ và màn Xu hướng.
 */

export const trendsKeys = {
  all: ["trends"] as const,
  byRange: (catId: string, range: TrendRange) => [...trendsKeys.all, catId, range] as const,
};

export function useCatTrends(catId: string | undefined, range: TrendRange): UseQueryResult<CatTrendsResponse> {
  return useQuery({
    queryKey: trendsKeys.byRange(catId ?? "", range),
    queryFn: () => {
      if (!catId) throw new Error("catId is required");
      return fetchCatTrends(catId, range);
    },
    enabled: Boolean(catId),
    // 403 FEATURE_NOT_IN_PLAN / 404 không bao giờ tự khỏi khi thử lại — retry chỉ làm màn
    // "khoá gói" hiện chậm hơn vài giây.
    retry: false,
  });
}

/**
 * Chuỗi điểm để vẽ: bỏ lần quét `INCONCLUSIVE` (`phValue === null`) và sắp theo thời gian
 * tăng dần. Điểm cuối được đánh dấu `isLatest` cho chú giải "lần đo gần nhất".
 */
export function toTrendPoints(points: CatTrendPoint[] | undefined): TrendPoint[] {
  const measured = (points ?? [])
    .filter((p): p is CatTrendPoint & { phValue: number } => p.phValue !== null)
    .sort((a, b) => new Date(a.capturedAt).getTime() - new Date(b.capturedAt).getTime());
  return measured.map((p, index) => ({
    date: p.capturedAt,
    phValue: p.phValue,
    classification: p.classification,
    isLatest: index === measured.length - 1,
  }));
}

/**
 * Phân bố % theo phân loại, đếm TỪ CHÍNH `points` của D13 — không gọi thêm
 * `GET /scans/summary`: hai nguồn đếm trên hai cửa sổ thời gian khác nhau là cách cũ làm
 * lệch số giữa biểu đồ và thanh phân bố.
 */
export function useDistributionBuckets(points: CatTrendPoint[] | undefined): DistributionBucket[] {
  return useMemo(() => {
    const items = points ?? [];
    if (items.length === 0) return [];
    const counts = new Map<string, number>();
    for (const point of items) {
      counts.set(point.classification, (counts.get(point.classification) ?? 0) + 1);
    }
    return [...counts.entries()].map(([classification, count]) => ({
      classification,
      count,
      percent: Math.round((count / items.length) * 100),
    }));
  }, [points]);
}
