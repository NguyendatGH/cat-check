import {
  useMutation,
  useQueries,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { Cat } from "@/entities/cat";
import type { ScanAnalysisDetail, ScanConfig, ScanListItem, ScanListPage, ScanResult } from "@/entities/scan-result";
import { apiFetch, apiSubmitScan, listActiveCats } from "./api";
import type { DisputeResult, ReassignResult, SubmitScanMetadata } from "./types";

/**
 * Hooks react-query cho `features/scan` — mọi call đi qua `apiFetch`/`apiSubmitScan`
 * (features/scan/api.ts) theo hợp đồng p8 §8.5.4 nhóm E. W3 nối API thật: thay `apiFetch` bằng
 * `apiClient` + type OpenAPI, hooks không đổi chữ ký.
 */

export const scanKeys = {
  all: ["scan"] as const,
  config: () => [...scanKeys.all, "config"] as const,
  detail: (scanId: string) => [...scanKeys.all, "detail", scanId] as const,
  byRequest: (scanRequestId: string) => [...scanKeys.all, "byRequest", scanRequestId] as const,
  activeCats: () => [...scanKeys.all, "activeCats"] as const,
  recentByCat: (catId: string, limit: number) => [...scanKeys.all, "recentByCat", catId, limit] as const,
};

/** E12 — tham số/ngưỡng client (kích thước ảnh tối đa, precheck...). */
export function useScanConfig(): UseQueryResult<ScanConfig> {
  return useQuery({
    queryKey: scanKeys.config(),
    queryFn: () => apiFetch<ScanConfig>("/scan/config"),
    staleTime: 10 * 60 * 1000,
  });
}

/** D1 — danh sách mèo ACTIVE để chọn trước khi quét (`/scan/select-cat`). */
export function useActiveCatsForCapture(): UseQueryResult<Cat[]> {
  return useQuery({
    queryKey: scanKeys.activeCats(),
    queryFn: listActiveCats,
    staleTime: 60 * 1000,
  });
}

/** E1 — phân tích + lưu + trừ credit một bước. Trả `200` cả khi INCONCLUSIVE. */
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
      // Số dư/sổ credit (`features/credit`, khoá `['credit', …]`) đổi ngay khi lượt quét được lưu —
      // chỉ là khoá chuỗi dùng chung, không import chéo feature.
      void queryClient.invalidateQueries({ queryKey: ["credit"] });
    },
  });
}

/** E4 — chi tiết một lần quét (kết quả hiển thị). */
export function useScan(scanId: string | undefined): UseQueryResult<ScanResult> {
  return useQuery({
    queryKey: scanKeys.detail(scanId ?? ""),
    queryFn: () => apiFetch<ScanResult>(`/scans/${scanId ?? ""}`),
    enabled: Boolean(scanId),
  });
}

/** E5 — phân tích chi tiết (Lab, ΔE, hiệu chỉnh, chất lượng) — màn "Phân tích chi tiết". */
export function useScanAnalysis(scanId: string | undefined): UseQueryResult<ScanAnalysisDetail> {
  return useQuery({
    queryKey: [...scanKeys.detail(scanId ?? ""), "analysis"],
    queryFn: () => apiFetch<ScanAnalysisDetail>(`/scans/${scanId ?? ""}/analysis`),
    enabled: Boolean(scanId),
  });
}

/** E6 — poll kết quả theo `scanRequestId` sau 409 `SCAN_IN_PROGRESS`. */
export function useScanByRequest(scanRequestId: string | undefined): UseQueryResult<ScanResult> {
  return useQuery({
    queryKey: scanKeys.byRequest(scanRequestId ?? ""),
    queryFn: () => apiFetch<ScanResult>(`/scans/by-request/${scanRequestId ?? ""}`),
    enabled: Boolean(scanRequestId),
  });
}

/** E9 — gán lại kết quả cho mèo khác (≤24h, ≤3 lần) hoặc "chưa rõ/dùng chung". */
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

/** E10 — đánh dấu kết quả không chính xác (loại khỏi trends). */
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

/** E11 — gỡ đánh dấu tranh chấp. */
export function useClearScanDispute(scanId: string): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/scans/${scanId}/dispute`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: scanKeys.detail(scanId) });
    },
  });
}

/** E8 — xoá mềm một lần quét + xoá cứng file ảnh. */
export function useDeleteScan(scanId: string): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>(`/scans/${scanId}`, { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: [...scanKeys.all, "recentByCat"] });
    },
  });
}

/**
 * E2 `GET /scans?catId=…&limit=…` — vài lượt quét MỚI NHẤT của một bé (đã sắp giảm dần theo
 * `capturedAt`, đã loại `INCONCLUSIVE` phía server). Dùng cho dải "các lần quét gần đây" ở màn
 * kết quả và dòng "quét gần nhất" ở màn chọn mèo — `GET /cats` KHÔNG trả `lastScanAt`.
 */
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

/**
 * Lượt quét gần nhất của TỪNG bé trong danh sách — `undefined` khi đang tải/lỗi (không đoán),
 * `null` khi bé thật sự chưa có lượt quét nào.
 */
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
