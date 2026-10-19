import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryOptions,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { Cat } from "@/entities/cat";
import type { ScanAnalysisDetail, ScanConfig, ScanListItem, ScanListPage, ScanResult } from "@/entities/scan-result";
import { apiFetch, apiSubmitScan, listActiveCats } from "./api";
import type { DisputeResult, ReassignResult, SubmitScanMetadata } from "./types";


export const scanKeys = {
  all: ["scan"] as const,
  config: () => [...scanKeys.all, "config"] as const,
  detail: (scanId: string) => [...scanKeys.all, "detail", scanId] as const,
  byRequest: (scanRequestId: string) => [...scanKeys.all, "byRequest", scanRequestId] as const,
  activeCats: () => [...scanKeys.all, "activeCats"] as const,
  recentByCat: (catId: string, limit: number) => [...scanKeys.all, "recentByCat", catId, limit] as const,
};

export function useScanConfig(): UseQueryResult<ScanConfig> {
  return useQuery({
    queryKey: scanKeys.config(),
    queryFn: () => apiFetch<ScanConfig>("/scan/config"),
    staleTime: 10 * 60 * 1000,
  });
}

export function useActiveCatsForCapture(): UseQueryResult<Cat[]> {
  return useQuery({
    queryKey: scanKeys.activeCats(),
    queryFn: listActiveCats,
    staleTime: 60 * 1000,
  });
}

export function useSubmitScan(): UseMutationResult<ScanResult, Error, { metadata: SubmitScanMetadata; file: File }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ metadata, file }) => apiSubmitScan(metadata, file),
    onSuccess: (result) => {
      if (result.scanId) {
        queryClient.setQueryData(scanKeys.detail(result.scanId), result);
      }
      queryClient.setQueryData(scanKeys.byRequest(result.scanRequestId), result);
      void queryClient.invalidateQueries({ queryKey: [...scanKeys.all, "recentByCat"] });
      void queryClient.invalidateQueries({ queryKey: ["credit"] });
    },
  });
}

export function useScan(scanId: string | undefined): UseQueryResult<ScanResult> {
  return useQuery({
    queryKey: scanKeys.detail(scanId ?? ""),
    queryFn: () => apiFetch<ScanResult>(`/scans/${scanId ?? ""}`),
    enabled: Boolean(scanId),
  });
}

export function useScanAnalysis(scanId: string | undefined): UseQueryResult<ScanAnalysisDetail> {
  return useQuery({
    queryKey: [...scanKeys.detail(scanId ?? ""), "analysis"],
    queryFn: () => apiFetch<ScanAnalysisDetail>(`/scans/${scanId ?? ""}/analysis`),
    enabled: Boolean(scanId),
  });
}

export function useScanByRequest(
  scanRequestId: string | undefined,
  options: {
    enabled?: boolean;
    refetchInterval?: UseQueryOptions<ScanResult>["refetchInterval"];
  } = {},
): UseQueryResult<ScanResult> {
  return useQuery({
    queryKey: scanKeys.byRequest(scanRequestId ?? ""),
    queryFn: () => apiFetch<ScanResult>(`/scans/by-request/${scanRequestId ?? ""}`),
    enabled: Boolean(scanRequestId) && (options.enabled ?? true),
    refetchInterval: options.refetchInterval,
  });
}

export function useReassignScanCat(
  scanId: string,
): UseMutationResult<ReassignResult, Error, { toCatId: string | null; toAssignment?: "SHARED_UNKNOWN" }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch<ReassignResult>(`/scans/${scanId}/reassign-cat`, {
        method: "POST",
        body: JSON.stringify(body),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scanKeys.detail(scanId) });
      void queryClient.invalidateQueries({ queryKey: [...scanKeys.all, "recentByCat"] });
    },
  });
}

export function useDisputeScan(scanId: string): UseMutationResult<DisputeResult, Error, { note?: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body) =>
      apiFetch<DisputeResult>(`/scans/${scanId}/dispute`, { method: "POST", body: JSON.stringify(body) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scanKeys.detail(scanId) });
    },
  });
}

export function useClearScanDispute(scanId: string): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/scans/${scanId}/dispute`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scanKeys.detail(scanId) });
    },
  });
}

export function useDeleteScan(scanId: string): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/scans/${scanId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...scanKeys.all, "recentByCat"] });
    },
  });
}

function fetchRecentCatScans(catId: string, limit: number): Promise<ScanListItem[]> {
  const params = new URLSearchParams({ catId, limit: String(limit) });
  return apiFetch<ScanListPage>(`/scans?${params.toString()}`).then((page) => page.items);
}

export function useRecentCatScans(catId: string | null | undefined, limit: number): UseQueryResult<ScanListItem[]> {
  return useQuery({
    queryKey: scanKeys.recentByCat(catId ?? "", limit),
    queryFn: () => fetchRecentCatScans(catId ?? "", limit),
    enabled: Boolean(catId),
    staleTime: 30 * 1000,
  });
}

export function useLatestScanByCat(catIds: string[]): Record<string, ScanListItem | null | undefined> {
  const results = useQueries({
    queries: catIds.map((catId) => ({
      queryKey: scanKeys.recentByCat(catId, 1),
      queryFn: () => fetchRecentCatScans(catId, 1),
      staleTime: 30 * 1000,
    })),
  });
  const byCat: Record<string, ScanListItem | null | undefined> = {};
  catIds.forEach((catId, index) => {
    const items = results[index]?.data;
    byCat[catId] = items === undefined ? undefined : (items[0] ?? null);
  });
  return byCat;
}
