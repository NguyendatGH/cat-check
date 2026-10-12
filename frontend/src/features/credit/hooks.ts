import {
  useInfiniteQuery,
  useMutation,
  useQuery,
  useQueryClient,
  type InfiniteData,
  type UseInfiniteQueryResult,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { ActivationResult, CreditBalance, Entitlement, LedgerPage, PackagePlan, PackagePlanList } from "./types";

export const creditKeys = {
  all: ["credit"] as const,
  balance: () => [...creditKeys.all, "balance"] as const,
  ledger: () => [...creditKeys.all, "ledger"] as const,
};
export const entitlementKey = ["entitlement"] as const;
export const packageCatalogKey = ["credit", "package-catalog"] as const;

export function usePackageCatalog(): UseQueryResult<PackagePlan[]> {
  return useQuery({
    queryKey: packageCatalogKey,
    queryFn: async () => (await apiFetch<PackagePlanList>("/reference/packages")).items,
    staleTime: 5 * 60 * 1000,
    gcTime: 30 * 60 * 1000,
  });
}

export function useCreditBalance(): UseQueryResult<CreditBalance> {
  return useQuery({
    queryKey: creditKeys.balance(),
    queryFn: () => apiFetch<CreditBalance>("/credits/balance"),
    staleTime: 0,
    gcTime: 5 * 60 * 1000,
    refetchInterval: 60 * 1000,
  });
}

export function useCreditLedger(): UseInfiniteQueryResult<InfiniteData<LedgerPage, string | null>> {
  return useInfiniteQuery({
    queryKey: creditKeys.ledger(),
    queryFn: ({ pageParam }: { pageParam: string | null }) => {
      const qs = pageParam ? `?cursor=${encodeURIComponent(pageParam)}` : "";
      return apiFetch<LedgerPage>(`/credits/ledger${qs}`);
    },
    initialPageParam: null,
    getNextPageParam: (lastPage) => (lastPage.hasMore ? lastPage.nextCursor : undefined),
    staleTime: 2 * 60 * 1000,
  });
}

export function useEntitlement(enabled = true): UseQueryResult<Entitlement> {
  return useQuery({
    queryKey: entitlementKey,
    queryFn: () => apiFetch<Entitlement>("/entitlements/me"),
    staleTime: 60 * 1000,
    gcTime: 10 * 60 * 1000,
    enabled,
    retry: false,
    refetchOnWindowFocus: false,
  });
}

export function useActivateCode(): UseMutationResult<ActivationResult, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (code: string) =>
      apiFetch<ActivationResult>("/activations", { method: "POST", body: JSON.stringify({ code }) }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: creditKeys.balance() });
      void queryClient.invalidateQueries({ queryKey: creditKeys.ledger() });
      void queryClient.invalidateQueries({ queryKey: entitlementKey });
      void queryClient.invalidateQueries({ queryKey: ["session"] });
    },
  });
}
