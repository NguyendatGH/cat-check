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
import type { ActivationResult, CreditBalance, Entitlement, LedgerPage } from "./types";

/**
 * Query key factory — domain `credit` thuộc `features/credit` (p9 §9.5.3):
 * `['credit','balance']` / `['credit','ledger', page]`. `entitlement` là ngoại lệ — khoá
 * `['entitlement']` (không tiền tố `credit`) vì p9 xếp nó vào miền dùng chung của
 * `entities/user` (mọi feature gate UI theo entitlement, không riêng credit).
 */
export const creditKeys = {
  all: ["credit"] as const,
  balance: () => [...creditKeys.all, "balance"] as const,
  ledger: () => [...creditKeys.all, "ledger"] as const,
};
export const entitlementKey = ["entitlement"] as const;

/**
 * H2 — số dư credit. `staleTime: 0` (p9 §9.5.4): số dư + đếm ngược hết hạn phải luôn đúng.
 * `refetchInterval` 60s để đồng hồ đếm ngược của từng lô không đứng yên khi màn đang mở.
 */
export function useCreditBalance(): UseQueryResult<CreditBalance> {
  return useQuery({
    queryKey: creditKeys.balance(),
    queryFn: () => apiFetch<CreditBalance>("/credits/balance"),
    staleTime: 0,
    gcTime: 5 * 60 * 1000,
    refetchInterval: 60 * 1000,
  });
}

/** H3 — lịch sử giao dịch, phân trang con trỏ mờ (keyset theo `created_at, id`). */
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

/** H4 — quyền hiện tại. Không cache lâu: quyền phải có hiệu lực ngay sau khi kích hoạt. */
export function useEntitlement(): UseQueryResult<Entitlement> {
  return useQuery({
    queryKey: entitlementKey,
    queryFn: () => apiFetch<Entitlement>("/entitlements/me"),
    staleTime: 60 * 1000,
    gcTime: 10 * 60 * 1000,
  });
}

/**
 * H1 — kích hoạt mã. Sau khi thành công: invalidate `credit.balance`, `credit.ledger`,
 * `entitlement`, `session` (p9 §9.5.5) — `session` thuộc `entities/user`, cũng chỉ là 1 key
 * string dùng chung, không phải import chéo module.
 */
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
