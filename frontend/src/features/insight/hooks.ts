import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { HealthFlagFilter, HealthFlagListResponse, HealthFlagView } from "./types";

/**
 * Hooks react-query cho nhóm G (p8 §8.4.7): danh sách dấu hiệu theo dõi, chi tiết một dấu
 * hiệu và thao tác "Đã hiểu".
 *
 * `cat.unacknowledgedFlagCount` (D10/D12) là con số TÓM TẮT do server tính; nó không kèm nội
 * dung nên trước đây chỉ là một badge chết. Ba endpoint dưới đây là phần còn thiếu để con số
 * đó bấm được: mở ra danh sách thật và xác nhận được từng mục.
 */

export const insightKeys = {
  all: ["insight"] as const,
  flags: (filter: HealthFlagFilter = {}) =>
    [
      ...insightKeys.all,
      "health-flags",
      filter.catId ?? null,
      filter.acknowledged ?? null,
      filter.severity ?? null,
      filter.limit ?? null,
    ] as const,
  flag: (flagId: string) => [...insightKeys.all, "health-flag", flagId] as const,
};

function toQueryString(filter: HealthFlagFilter): string {
  const qs = new URLSearchParams();
  if (filter.catId !== undefined) qs.set("catId", filter.catId);
  if (filter.acknowledged !== undefined) qs.set("acknowledged", String(filter.acknowledged));
  if (filter.severity !== undefined) qs.set("severity", filter.severity);
  if (filter.limit !== undefined) qs.set("limit", String(filter.limit));
  const value = qs.toString();
  return value ? `?${value}` : "";
}

/** G1 — `GET /health-flags`. Lọc theo `catId`/`acknowledged`/`severity`. */
export function useHealthFlags(filter: HealthFlagFilter = {}, enabled = true): UseQueryResult<HealthFlagListResponse> {
  return useQuery({
    queryKey: insightKeys.flags(filter),
    queryFn: () => apiFetch<HealthFlagListResponse>(`/health-flags${toQueryString(filter)}`),
    enabled,
    staleTime: 60 * 1000,
  });
}

/** G2 — `GET /health-flags/{flagId}`. */
export function useHealthFlag(flagId: string | undefined): UseQueryResult<HealthFlagView> {
  return useQuery({
    queryKey: insightKeys.flag(flagId ?? ""),
    queryFn: () => {
      if (!flagId) throw new Error("flagId is required");
      return apiFetch<HealthFlagView>(`/health-flags/${flagId}`);
    },
    enabled: flagId !== undefined,
  });
}

/**
 * G3 — `POST /health-flags/{flagId}/acknowledge` (204). Sau khi ghi nhận phải làm mới CẢ
 * danh sách cảnh báo LẪN hồ sơ mèo: `unacknowledgedFlagCount` nằm trong `GET /cats` và
 * `GET /cats/{id}/summary`, không tự giảm theo.
 */
export function useAcknowledgeHealthFlag(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (flagId: string) => apiFetch<undefined>(`/health-flags/${flagId}/acknowledge`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: insightKeys.all });
      // `['cat']` là `catKeys.all` của `features/cat` (p9 §9.5.3) và `['history']` là
      // `historyKeys.all`. Chỉ dùng khoá string dùng chung — boundaries cấm feature import
      // feature, nên KHÔNG import `catKeys` vào đây.
      void queryClient.invalidateQueries({ queryKey: ["cat"] });
      void queryClient.invalidateQueries({ queryKey: ["history"] });
    },
  });
}
