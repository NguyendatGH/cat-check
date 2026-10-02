import {
  useInfiniteQuery,
  useQuery,
  type InfiniteData,
  type UseInfiniteQueryResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { Cat } from "@/entities/cat";
import type { ScanListPage, ScanSummary } from "@/entities/scan-result";
import { fetchCat, fetchScanHistory, fetchScanSummary } from "./api";
import type { HistoryFilterKey } from "./types";

/**
 * Hooks react-query cho `features/history` (p8 §8.5.4 E2/E3). `useScanHistory` dùng
 * `useInfiniteQuery` vì E2 là phân trang con trỏ (cursor) — không phải offset.
 */

const HISTORY_PAGE_SIZE = 20;

export const historyKeys = {
  all: ["history"] as const,
  list: (catId: string | undefined, filter: HistoryFilterKey) =>
    [...historyKeys.all, "list", catId ?? "any", filter] as const,
  summary: (catId: string | undefined, from?: string, to?: string) =>
    [...historyKeys.all, "summary", catId ?? "any", from ?? "", to ?? ""] as const,
};

/**
 * @param enabled đặt `false` cho khách chưa đăng nhập — `/scans` yêu cầu phiên (xem
 *                {@link useCatList}).
 */
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
    getNextPageParam: (lastPage: ScanListPage) => (lastPage.hasMore ? lastPage.nextCursor : undefined),
  });
}

export function useScanSummary(catId: string | undefined, from?: string, to?: string): UseQueryResult<ScanSummary> {
  return useQuery({
    queryKey: historyKeys.summary(catId, from, to),
    queryFn: () => fetchScanSummary({ catId, from, to }),
  });
}

export function useCat(catId: string | undefined): UseQueryResult<Cat> {
  return useQuery({
    queryKey: [...historyKeys.all, "cat", catId ?? ""],
    queryFn: () => fetchCat(catId ?? ""),
    enabled: Boolean(catId),
  });
}
