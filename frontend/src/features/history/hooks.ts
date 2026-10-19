import { useMemo } from "react";
import {
  useInfiniteQuery,
  useQueries,
  useQuery,
  type InfiniteData,
  type UseInfiniteQueryResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { Cat } from "@/entities/cat";
import type { ScanListItem, ScanListPage, ScanSummary } from "@/entities/scan-result";
import { fetchCat, fetchScanHistory, fetchScanSummary } from "./api";
import type { HistoryFilterKey } from "./types";


const HISTORY_PAGE_SIZE = 20;

export const historyKeys = {
  all: ["history"] as const,
  list: (catId: string | undefined, filter: HistoryFilterKey) =>
    [...historyKeys.all, "list", catId ?? "any", filter] as const,
  summary: (catId: string | undefined, from?: string, to?: string) =>
    [...historyKeys.all, "summary", catId ?? "any", from ?? "", to ?? ""] as const,
  latest: (catId: string) => [...historyKeys.all, "latest", catId] as const,
};

export function useScanHistory(
  catId: string | undefined,
  filter: HistoryFilterKey,
  enabled = true,
): UseInfiniteQueryResult<InfiniteData<ScanListPage>> {
  return useInfiniteQuery({
    enabled,
    queryKey: historyKeys.list(catId, filter),
    queryFn: ({ pageParam }: { pageParam: string | null }) =>
      fetchScanHistory({ catId, filter, cursor: pageParam, limit: HISTORY_PAGE_SIZE }),
    initialPageParam: null as string | null,
    getNextPageParam: (lastPage: ScanListPage) => (lastPage.hasMore ? (lastPage.nextCursor ?? undefined) : undefined),
  });
}

export function useScanSummary(
  catId: string | undefined,
  from?: string,
  to?: string,
  enabled = true,
): UseQueryResult<ScanSummary> {
  return useQuery({
    enabled,
    queryKey: historyKeys.summary(catId, from, to),
    queryFn: () => fetchScanSummary({ catId, from, to }),
    // Keep the previous window's numbers while a new range loads, but never another cat's.
    placeholderData: (previous, previousQuery) =>
      previousQuery?.queryKey[2] === (catId ?? "any") ? previous : undefined,
  });
}

export function useCat(catId: string | undefined): UseQueryResult<Cat> {
  return useQuery({
    queryKey: [...historyKeys.all, "cat", catId ?? ""],
    queryFn: () => fetchCat(catId ?? ""),
    enabled: Boolean(catId),
  });
}

/**
 * Latest scan per cat. Uses the already-loaded recent scans first; a cat that has scanned before
 * but is missing from them (others scanned more recently) gets its own one-item lookup.
 */
export function useLatestScanByCat(
  cats: readonly Pick<Cat, "id" | "lastScanAt">[],
  recentScans: readonly ScanListItem[],
  enabled = true,
): Map<string, ScanListItem> {
  const fromRecent = useMemo(() => {
    const map = new Map<string, ScanListItem>();
    for (const scan of recentScans) {
      if (scan.catId != null && !map.has(scan.catId)) map.set(scan.catId, scan);
    }
    return map;
  }, [recentScans]);
  const missing = cats.filter((cat) => cat.lastScanAt != null && !fromRecent.has(cat.id));
  const lookups = useQueries({
    queries: missing.map((cat) => ({
      enabled,
      queryKey: historyKeys.latest(cat.id),
      queryFn: () => fetchScanHistory({ catId: cat.id, filter: "ALL", cursor: null, limit: 1 }),
    })),
  });
  const found = lookups.map((lookup) => lookup.data?.items[0]);
  const foundKey = found.map((scan) => scan?.scanId ?? "").join(",");
  return useMemo(() => {
    const map = new Map(fromRecent);
    for (const scan of found) {
      if (scan?.catId != null && !map.has(scan.catId)) map.set(scan.catId, scan);
    }
    return map;
    // foundKey captures the lookup results; `found` itself is a new array every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fromRecent, foundKey]);
}
