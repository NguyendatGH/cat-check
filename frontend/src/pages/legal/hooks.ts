import { useMutation, useQuery, type UseMutationResult, type UseQueryResult } from "@tanstack/react-query";
import { apiFetch } from "./api";
import type {
  DsarRequestType,
  DsarRequestView,
  PageView,
  PolicyCode,
  PolicyVersionListResponse,
  PolicyVersionSummaryView,
  PolicyView,
  PurposeView,
} from "./types";


export function usePolicyCurrent(policyCode: PolicyCode, locale: "vi" | "en" = "vi"): UseQueryResult<PolicyView> {
  return useQuery({
    queryKey: ["legal", "policy", policyCode, locale],
    queryFn: () => apiFetch<PolicyView>(`/policies/${policyCode}?locale=${locale}`),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function usePolicyVersions(
  policyCode: PolicyCode | undefined,
  locale: "vi" | "en" = "vi",
): UseQueryResult<PolicyVersionSummaryView[]> {
  return useQuery({
    queryKey: ["legal", "policy-versions", policyCode, locale],
    queryFn: async () => {
      if (!policyCode) throw new Error("policyCode is required");
      const page = await apiFetch<PolicyVersionListResponse>(`/policies/${policyCode}/versions?locale=${locale}`);
      return page.items;
    },
    enabled: policyCode !== undefined,
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function usePolicyVersion(
  policyCode: PolicyCode | undefined,
  version: string | undefined,
  locale: "vi" | "en" = "vi",
): UseQueryResult<PolicyView> {
  return useQuery({
    queryKey: ["legal", "policy-version", policyCode, version, locale],
    queryFn: () => {
      if (!policyCode || !version) throw new Error("policyCode/version is required");
      return apiFetch<PolicyView>(`/policies/${policyCode}/versions/${encodeURIComponent(version)}?locale=${locale}`);
    },
    enabled: policyCode !== undefined && version !== undefined,
    retry: false,
    staleTime: 60 * 60 * 1000,
  });
}

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
