import { useMemo } from "react";
import { useQuery, type UseQueryResult } from "@tanstack/react-query";
import { fetchCatTrends } from "./api";
import type { CatTrendPoint, CatTrendsResponse, DistributionBucket, TrendPoint, TrendRange } from "./types";


export const trendsKeys = {
  all: ["trends"] as const,
  byRange: (catId: string, range: TrendRange) => [...trendsKeys.all, catId, range] as const,
};

export function useCatTrends(
  catId: string | undefined,
  range: TrendRange,
  enabled = true,
): UseQueryResult<CatTrendsResponse> {
  return useQuery({
    queryKey: trendsKeys.byRange(catId ?? "", range),
    queryFn: () => {
      if (!catId) throw new Error("catId is required");
      return fetchCatTrends(catId, range);
    },
    enabled: Boolean(catId) && enabled,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function toTrendPoints(points: CatTrendPoint[] | undefined): TrendPoint[] {
  const measured = (points ?? [])
    .filter((p): p is CatTrendPoint & { phValue: number } => typeof p.phValue === "number")
    .sort((a, b) => new Date(a.capturedAt).getTime() - new Date(b.capturedAt).getTime());
  return measured.map((p, index) => ({
    date: p.capturedAt,
    phValue: p.phValue,
    classification: p.classification,
    isLatest: index === measured.length - 1,
  }));
}

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
