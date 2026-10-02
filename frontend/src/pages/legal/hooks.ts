import { useMutation, useQuery, type UseMutationResult, type UseQueryResult } from "@tanstack/react-query";
import { apiFetch } from "./api";
import type { DsarRequestType, DsarRequestView, PageView, PolicyCode, PolicyView, PurposeView } from "./types";

/**
 * Hooks react-query cho các trang pháp lý — gọi thẳng `PolicyController`/`PrivacyController`
 * thật (p8 §8.4.3). Không có `features/legal/` (ngoài phạm vi A2-FE) nên đặt cạnh page.
 */

/** C16 — bản hiện hành của một tài liệu pháp lý, công khai. */
export function usePolicyCurrent(policyCode: PolicyCode, locale: "vi" | "en" = "vi"): UseQueryResult<PolicyView> {
  return useQuery({
    queryKey: ["legal", "policy", policyCode, locale],
    queryFn: () => apiFetch<PolicyView>(`/policies/${policyCode}?locale=${locale}`),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

/** C1 — danh mục mục đích xử lý dữ liệu, công khai (dùng cho màn hướng dẫn DSAR). */
export function useConsentPurposes(): UseQueryResult<PurposeView[]> {
  return useQuery({
    queryKey: ["legal", "consent-purposes"],
    queryFn: async () => {
      const page = await apiFetch<PageView<PurposeView>>("/privacy/purposes?locale=vi");
      return page.items;
    },
    staleTime: 10 * 60 * 1000,
  });
}

/** C13 — tạo yêu cầu DSAR không tự phục vụ được (RECTIFY/OBJECT/PROTECTION_MEASURE/COMPLAINT). */
export function useCreateDsarRequest(): UseMutationResult<
  DsarRequestView,
  Error,
  { requestType: DsarRequestType; channel?: string }
> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<DsarRequestView>("/privacy/requests", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}
