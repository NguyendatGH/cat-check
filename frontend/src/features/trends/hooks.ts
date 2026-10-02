import { useMemo } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import type { ScanSummary } from "@/entities/scan-result";
import { fetchAllScansInRange, fetchScanSummary } from "./api";
import { TREND_RANGE_DAYS, type DistributionBucket, type TrendPoint, type TrendRange } from "./types";

export const trendsKeys = {
  all: ["trends"] as const,
  series: (catId: string, range: TrendRange) => [...trendsKeys.all, "series", catId, range] as const,
  summary: (catId: string, range: TrendRange) => [...trendsKeys.all, "summary", catId, range] as const,
};

function rangeToDates(range: TrendRange): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - TREND_RANGE_DAYS[range] * 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

export interface TrendSeriesResult {
  points: TrendPoint[];
}

/** Tự dựng chuỗi xu hướng từ E2 (`GET /scans`) — xem `types.ts` vì sao không dùng D13. */
export function useTrendSeries(catId: string | undefined, range: TrendRange): UseQueryResult<TrendSeriesResult> {
  return useQuery({
    queryKey: trendsKeys.series(catId ?? "", range),
    queryFn: async () => {
      if (!catId) throw new Error("catId is required");
      const { from, to } = rangeToDates(range);
      const scans = await fetchAllScansInRange(catId, from, to);
      const sorted = [...scans]
        .filter((s) => s.phValue !== null)
        .sort((a, b) => new Date(a.capturedAt).getTime() - new Date(b.capturedAt).getTime());
      const points: TrendPoint[] = sorted.map((s, index) => ({
        scanId: s.scanId,
        date: s.capturedAt,
        // eslint-disable-next-line @typescript-eslint/no-non-null-assertion -- lọc null ở filter trên
        phValue: s.phValue!,
        classification: s.classification,
        isLatest: index === sorted.length - 1,
      }));
      return { points };
    },
    enabled: Boolean(catId),
  });
}

export function useTrendSummary(catId: string | undefined, range: TrendRange): UseQueryResult<ScanSummary> {
  return useQuery({
    queryKey: trendsKeys.summary(catId ?? "", range),
    queryFn: () => {
      if (!catId) throw new Error("catId is required");
      const { from, to } = rangeToDates(range);
      return fetchScanSummary(catId, from, to);
    },
    enabled: Boolean(catId),
  });
}

/** Suy ra phân bố % theo phân loại từ `ScanSummary.byClassification` (E3) — không gọi thêm API. */
export function useDistributionBuckets(summary: ScanSummary | undefined): DistributionBucket[] {
  return useMemo(() => {
    if (!summary || summary.count === 0) return [];
    return Object.entries(summary.byClassification).map(([classification, count]) => ({
      classification,
      count,
      percent: Math.round((count / summary.count) * 100),
    }));
  }, [summary]);
}
