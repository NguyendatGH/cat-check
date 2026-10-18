import { useMutation, useQuery, type UseMutationResult, type UseQueryResult } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { isApiError } from "@/shared/api";
import { apiFetch } from "./api";
import type {
  CareTipView,
  DsarRequestType,
  DsarRequestView,
  PageView,
  PolicyCode,
  PolicyVersionListResponse,
  PolicyVersionSummaryView,
  PolicyView,
  PurposeView,
} from "./types";


type PolicyLocale = "vi" | "en";

function usePolicyLocale(): PolicyLocale {
  const { i18n } = useTranslation();
  return i18n.language.startsWith("en") ? "en" : "vi";
}

export function usePolicyCurrent(policyCode: PolicyCode): UseQueryResult<PolicyView> {
  const locale = usePolicyLocale();
  return useQuery({
    queryKey: ["legal", "policy", policyCode, locale],
    queryFn: async () => {
      try {
        return await apiFetch<PolicyView>(`/policies/${policyCode}?locale=${locale}`);
      } catch (error) {
        // GET /policies/{code} has no server-side fallback and only "vi" is seeded: no "en" version yet → show "vi".
        if (locale === "vi" || !isApiError(error) || error.code !== "POLICY_VERSION_NOT_FOUND") throw error;
        return apiFetch<PolicyView>(`/policies/${policyCode}?locale=vi`);
      }
    },
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function useCareTip(slug: string): UseQueryResult<CareTipView> {
  const locale = usePolicyLocale();
  return useQuery({
    // The server resolves the tip by Accept-Language, so each language is its own cache entry.
    queryKey: ["legal", "care-tip", slug, locale],
    queryFn: () => apiFetch<CareTipView>(`/care-tips/${encodeURIComponent(slug)}`),
    enabled: slug !== "",
    retry: false,
    staleTime: 5 * 60 * 1000,
  });
}

export function usePolicyVersions(policyCode: PolicyCode | undefined): UseQueryResult<PolicyVersionSummaryView[]> {
  const locale = usePolicyLocale();
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
): UseQueryResult<PolicyView> {
  const locale = usePolicyLocale();
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
  const locale = usePolicyLocale();
  return useQuery({
    queryKey: ["legal", "consent-purposes", locale],
    queryFn: async () => {
      const page = await apiFetch<PageView<PurposeView>>(`/privacy/purposes?locale=${locale}`);
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
