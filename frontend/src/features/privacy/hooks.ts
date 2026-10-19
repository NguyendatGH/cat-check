import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import { apiFetch, downloadPrivacyExport } from "./api";
import type {
  AccessLogEntryView,
  ConsentHistoryView,
  ConsentStateView,
  DataInventoryView,
  DsarRequestView,
  ExportStatusView,
  ManualDsarType,
  OtpRequestResult,
  OtpVerifyResult,
  PageView,
  PurposeView,
  StepUpMethod,
} from "./types";


export const PRIVACY_KEYS = {
  purposes: ["privacy", "purposes"] as const,
  consents: ["privacy", "consents"] as const,
  consentHistory: ["privacy", "consents", "history"] as const,
  dataInventory: ["privacy", "data-inventory"] as const,
  requests: ["privacy", "requests"] as const,
  exportStatus: (publicRef: string) => ["privacy", "export", publicRef] as const,
  accessLog: ["privacy", "access-log"] as const,
};

export const DELETION_GRACE_DAYS = 7;

export function useConsentPurposes(): UseQueryResult<PurposeView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.purposes,
    queryFn: async () => (await apiFetch<PageView<PurposeView>>("/privacy/purposes")).items,
    staleTime: 10 * 60 * 1000,
  });
}

export function useCurrentConsents(): UseQueryResult<ConsentStateView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.consents,
    queryFn: async () => (await apiFetch<PageView<ConsentStateView>>("/privacy/consents")).items,
  });
}

export function useRecordConsents(): UseMutationResult<
  ConsentStateView[],
  Error,
  { purposeCode: string; granted: boolean }[]
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (consents) =>
      (
        await apiFetch<PageView<ConsentStateView>>("/privacy/consents?uiSurface=privacy_center", {
          method: "POST",
          body: JSON.stringify({ consents }),
        })
      ).items,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.consents });
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.consentHistory });
    },
  });
}

export function useConsentHistory(limit = 50): UseQueryResult<ConsentHistoryView[]> {
  return useQuery({
    queryKey: [...PRIVACY_KEYS.consentHistory, limit],
    queryFn: async () =>
      (await apiFetch<PageView<ConsentHistoryView>>(`/privacy/consents/history?limit=${String(limit)}`)).items,
  });
}

export function useDataInventory(): UseQueryResult<DataInventoryView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.dataInventory,
    queryFn: async () => (await apiFetch<PageView<DataInventoryView>>("/privacy/data-inventory")).items,
    staleTime: 10 * 60 * 1000,
  });
}

export function useDsarRequests(limit = 50): UseQueryResult<DsarRequestView[]> {
  return useQuery({
    queryKey: [...PRIVACY_KEYS.requests, limit],
    queryFn: async () => (await apiFetch<PageView<DsarRequestView>>(`/privacy/requests?limit=${String(limit)}`)).items,
  });
}

export function useExportStatus(publicRef: string | null): UseQueryResult<ExportStatusView> {
  return useQuery({
    queryKey: PRIVACY_KEYS.exportStatus(publicRef ?? ""),
    queryFn: () => apiFetch<ExportStatusView>(`/privacy/export/${publicRef ?? ""}`),
    enabled: publicRef !== null,
    refetchInterval: (query) => ["COMPLETED", "REJECTED"].includes(query.state.data?.status ?? "") ? false : 5_000,
  });
}

export function useCreateExportRequest(): UseMutationResult<ExportStatusView, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<ExportStatusView>("/privacy/export", { method: "POST" }),
    onSuccess: (data) => {
      if (data.downloadToken) {
        sessionStorage.setItem(`catcheck:dsar-download:${data.publicRef}`, data.downloadToken);
      }
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

export interface DsarExportDownloadInput { publicRef: string; downloadToken: string }

export function useDownloadExport(): UseMutationResult<Blob, Error, DsarExportDownloadInput> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ publicRef, downloadToken }) => downloadPrivacyExport(publicRef, downloadToken),
    onSuccess: (blob, { publicRef }) => {
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `${publicRef}.zip`;
      anchor.click();
      window.setTimeout(() => {
        URL.revokeObjectURL(url);
      }, 30_000);
      sessionStorage.removeItem(`catcheck:dsar-download:${publicRef}`);
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.exportStatus(publicRef) });
    },
  });
}

export function useRequestAccountDeletion(): UseMutationResult<ExportStatusView, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<ExportStatusView>("/privacy/account-deletion", { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

export function useCancelAccountDeletion(): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/privacy/account-deletion", { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

export function useSetRestriction(): UseMutationResult<undefined, Error, boolean> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) =>
      apiFetch<undefined>("/privacy/restriction", { method: enabled ? "POST" : "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

export function useCreateDsarRequest(): UseMutationResult<DsarRequestView, Error, ManualDsarType> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestType: ManualDsarType) =>
      apiFetch<DsarRequestView>("/privacy/requests", {
        method: "POST",
        body: JSON.stringify({ requestType, channel: "SELF_SERVICE" }),
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

export function useAccessLog(): UseQueryResult<AccessLogEntryView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.accessLog,
    queryFn: async () => (await apiFetch<{ items: AccessLogEntryView[] }>("/account/privacy/access-log")).items,
    staleTime: 60 * 1000,
  });
}


const STEP_UP_OTP_PURPOSE = "LOGIN_STEPUP";

export function useRequestStepUpOtp(): UseMutationResult<OtpRequestResult, Error, string> {
  return useMutation({
    mutationFn: (email: string) =>
      apiFetch<OtpRequestResult>("/auth/otp/request", {
        method: "POST",
        body: JSON.stringify({ email, purpose: STEP_UP_OTP_PURPOSE }),
      }),
  });
}

export function useVerifyStepUpOtp(): UseMutationResult<OtpVerifyResult, Error, { email: string; code: string }> {
  return useMutation({
    mutationFn: ({ email, code }) =>
      apiFetch<OtpVerifyResult>("/auth/otp/verify", {
        method: "POST",
        body: JSON.stringify({ email, purpose: STEP_UP_OTP_PURPOSE, code }),
      }),
  });
}

export function useReauth(): UseMutationResult<
  unknown,
  Error,
  { method: StepUpMethod; credential: string; targetAction: string }
> {
  return useMutation({
    mutationFn: (payload) => apiFetch<unknown>("/auth/reauth", { method: "POST", body: JSON.stringify(payload) }),
  });
}
