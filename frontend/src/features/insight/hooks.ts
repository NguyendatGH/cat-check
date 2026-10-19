import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { HealthFlagFilter, HealthFlagListResponse, HealthFlagView } from "./types";


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

export function useHealthFlags(filter: HealthFlagFilter = {}, enabled = true): UseQueryResult<HealthFlagListResponse> {
  return useQuery({
    queryKey: insightKeys.flags(filter),
    queryFn: () => apiFetch<HealthFlagListResponse>(`/health-flags${toQueryString(filter)}`),
    enabled,
    staleTime: 60 * 1000,
  });
}

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

export function useAcknowledgeHealthFlag(): UseMutationResult<undefined, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (flagId: string) => apiFetch<undefined>(`/health-flags/${flagId}/acknowledge`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: insightKeys.all });
      void queryClient.invalidateQueries({ queryKey: ["cat"] });
      void queryClient.invalidateQueries({ queryKey: ["history"] });
    },
  });
}
