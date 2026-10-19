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
import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { adminFetch, adminQuery, adminRequest } from "./api";
import type { PrivacyPage } from "./types";
import { classifyAdminError } from "./adminAccess";
import { reassignBody, replaceRule } from "./governanceForms";
import type {
  AdminConsentRecord,
  AdminMonitoringRule,
  AdminPolicyVersion,
  AdminPolicyVersionPage,
  AdminReassignResult,
  AdminScanList,
  AdminSecurityIncident,
  AdminSecurityIncidentPage,
  AdminUserCatList,
  CreatePolicyVersionPayload,
  IncidentSeverity,
  OpenSecurityIncidentPayload,
  PolicyLocale,
  PolicyType,
  PublishPolicyVersionPayload,
  PurgeAllSessionsResult,
  ReassignTarget,
  UpdateMonitoringRulePayload,
  UpdateSecurityIncidentPayload,
} from "./governanceTypes";

export const governanceKeys = {
  userScans: (userId: string, page: number, size: number) => ["admin", "user-scans", userId, page, size] as const,
  userScansAll: ["admin", "user-scans"] as const,
  userCats: (userId: string) => ["admin", "user-cats", userId] as const,
  monitoringRules: ["admin", "monitoring-rules"] as const,
  incidents: (filters: { severity: IncidentSeverity | ""; unresolved: boolean; page: number; size: number }) =>
    ["admin", "security-incidents", filters] as const,
  incidentsAll: ["admin", "security-incidents"] as const,
  incident: (incidentId: string) => ["admin", "security-incident", incidentId] as const,
  policyVersions: (filters: { policyType: PolicyType | ""; locale: PolicyLocale | ""; page: number; size: number }) =>
    ["admin", "policy-versions", filters] as const,
  policyVersionsAll: ["admin", "policy-versions"] as const,
  consents: (userId: string) => ["admin", "privacy-consents", userId] as const,
};

const AUDITED_QUERY = {
  retry: false,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
  staleTime: 5 * 60_000,
} as const;

export interface UserScansParams {
  userId: string;
  reason: string;
  page: number;
  size: number;
  enabled: boolean;
}

export function useAdminUserScans({
  userId,
  reason,
  page,
  size,
  enabled,
}: UserScansParams): UseQueryResult<AdminScanList> {
  return useQuery({
    queryKey: governanceKeys.userScans(userId, page, size),
    queryFn: () =>
      adminFetch<AdminScanList>(
        `/admin/users/${encodeURIComponent(userId)}/scans${adminQuery({ reason, page, size })}`,
      ),
    enabled,
    ...AUDITED_QUERY,
  });
}

export function useAdminUserCats(userId: string, reason: string, enabled: boolean): UseQueryResult<AdminUserCatList> {
  return useQuery({
    queryKey: governanceKeys.userCats(userId),
    queryFn: () =>
      adminFetch<AdminUserCatList>(`/admin/users/${encodeURIComponent(userId)}/cats${adminQuery({ reason })}`),
    enabled,
    ...AUDITED_QUERY,
  });
}

export interface ReassignScanVariables {
  scanId: string;
  target: ReassignTarget;
  reason: string;
}

export function useReassignAdminScan(): UseMutationResult<AdminReassignResult, Error, ReassignScanVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ scanId, target, reason }) =>
      adminFetch<AdminReassignResult>(`/admin/scans/${encodeURIComponent(scanId)}/reassign-cat`, {
        method: "POST",
        json: reassignBody(target, reason),
      }),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: governanceKeys.userScansAll });
    },
  });
}

interface ProblemDetail {
  detail?: string;
  errorCode?: string;
}

export async function fetchAdminScanImage(scanId: string, reason: string): Promise<Blob> {
  const response = await fetch(`/api/v1/admin/scans/${encodeURIComponent(scanId)}/image${adminQuery({ reason })}`, {
    credentials: "include",
    cache: "no-store",
    headers: { Accept: "image/*", "Accept-Language": currentAcceptLanguage() },
  });
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Body lỗi không phải JSON: giữ thông điệp mặc định theo mã HTTP.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return response.blob();
}

export function useAdminScanImage(): UseMutationResult<Blob, Error, { scanId: string; reason: string }> {
  return useMutation({ mutationFn: ({ scanId, reason }) => fetchAdminScanImage(scanId, reason) });
}

export function useAdminMonitoringRules(): UseQueryResult<AdminMonitoringRule[]> {
  return useQuery({
    queryKey: governanceKeys.monitoringRules,
    queryFn: async () => (await adminFetch<{ items: AdminMonitoringRule[] }>("/admin/monitoring-rules")).items,
    retry: false,
  });
}

export interface UpdateMonitoringRuleVariables {
  code: string;
  etag: string;
  payload: UpdateMonitoringRulePayload;
}

export function useUpdateMonitoringRule(): UseMutationResult<
  AdminMonitoringRule,
  Error,
  UpdateMonitoringRuleVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ code, etag, payload }) => {
      const envelope = await adminRequest<AdminMonitoringRule>(`/admin/monitoring-rules/${encodeURIComponent(code)}`, {
        method: "PATCH",
        json: payload,
        ifMatch: etag,
      });
      return envelope.etag !== null ? { ...envelope.data, etag: envelope.etag } : envelope.data;
    },
    onSuccess: (updated) => {
      queryClient.setQueryData<AdminMonitoringRule[]>(governanceKeys.monitoringRules, (current) =>
        current === undefined ? current : replaceRule(current, updated),
      );
    },
    onError: (error) => {
      if (classifyAdminError(error) === "stale") {
        void queryClient.invalidateQueries({ queryKey: governanceKeys.monitoringRules });
      }
    },
  });
}

export interface IncidentFilters {
  severity: IncidentSeverity | "";
  unresolved: boolean;
  page: number;
  size: number;
}

export function useAdminSecurityIncidents(
  filters: IncidentFilters,
  enabled = true,
): UseQueryResult<AdminSecurityIncidentPage> {
  return useQuery({
    queryKey: governanceKeys.incidents(filters),
    queryFn: () =>
      adminFetch<AdminSecurityIncidentPage>(
        `/admin/security/incidents${adminQuery({
          severity: filters.severity === "" ? undefined : filters.severity,
          unresolved: filters.unresolved ? "true" : undefined,
          page: filters.page,
          size: filters.size,
        })}`,
      ),
    enabled,
    retry: false,
  });
}

export function useAdminSecurityIncident(incidentId: string | null): UseQueryResult<AdminSecurityIncident> {
  return useQuery({
    queryKey: governanceKeys.incident(incidentId ?? ""),
    queryFn: () =>
      adminFetch<AdminSecurityIncident>(`/admin/security/incidents/${encodeURIComponent(incidentId ?? "")}`),
    enabled: incidentId !== null && incidentId !== "",
    retry: false,
  });
}

export function useOpenSecurityIncident(): UseMutationResult<
  AdminSecurityIncident,
  Error,
  OpenSecurityIncidentPayload
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      adminFetch<AdminSecurityIncident>("/admin/security/incidents", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: governanceKeys.incidentsAll });
    },
  });
}

export function useUpdateSecurityIncident(): UseMutationResult<
  AdminSecurityIncident,
  Error,
  { incidentId: string; payload: UpdateSecurityIncidentPayload }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ incidentId, payload }) =>
      adminFetch<AdminSecurityIncident>(`/admin/security/incidents/${encodeURIComponent(incidentId)}`, {
        method: "PATCH",
        json: payload,
      }),
    onSuccess: (updated) => {
      queryClient.setQueryData(governanceKeys.incident(updated.id), updated);
      void queryClient.invalidateQueries({ queryKey: governanceKeys.incidentsAll });
    },
  });
}

export function usePurgeAllSessions(): UseMutationResult<
  PurgeAllSessionsResult,
  Error,
  { incidentId: string; reason: string }
> {
  return useMutation({
    mutationFn: ({ incidentId, reason }) =>
      adminFetch<PurgeAllSessionsResult>("/admin/security/sessions/purge-all", {
        method: "POST",
        json: { incidentId, reason },
      }),
  });
}

export interface PolicyVersionFilters {
  policyType: PolicyType | "";
  locale: PolicyLocale | "";
  page: number;
  size: number;
}

export function useAdminPolicyVersions(filters: PolicyVersionFilters): UseQueryResult<AdminPolicyVersionPage> {
  return useQuery({
    queryKey: governanceKeys.policyVersions(filters),
    queryFn: () =>
      adminFetch<AdminPolicyVersionPage>(
        `/admin/policy-versions${adminQuery({
          policyType: filters.policyType,
          locale: filters.locale,
          page: filters.page,
          size: filters.size,
        })}`,
      ),
    retry: false,
  });
}

export function useCreatePolicyVersion(): UseMutationResult<AdminPolicyVersion, Error, CreatePolicyVersionPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      adminFetch<AdminPolicyVersion>("/admin/policy-versions", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: governanceKeys.policyVersionsAll });
    },
  });
}

export function usePublishPolicyVersion(): UseMutationResult<
  AdminPolicyVersion,
  Error,
  { id: string; payload: PublishPolicyVersionPayload }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }) =>
      adminFetch<AdminPolicyVersion>(`/admin/policy-versions/${encodeURIComponent(id)}/publish`, {
        method: "POST",
        json: payload,
      }),
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: governanceKeys.policyVersionsAll });
    },
  });
}

const CONSENT_PAGE_LIMIT = 100;

export function useAdminConsentRecords(
  userId: string,
  enabled: boolean,
): UseInfiniteQueryResult<InfiniteData<PrivacyPage<AdminConsentRecord>, string | null>> {
  return useInfiniteQuery({
    queryKey: governanceKeys.consents(userId),
    queryFn: ({ pageParam }) =>
      adminFetch<PrivacyPage<AdminConsentRecord>>(
        `/admin/privacy/consents${adminQuery({ userId, cursor: pageParam ?? undefined, limit: CONSENT_PAGE_LIMIT })}`,
      ),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => (last.hasMore && last.nextCursor !== null ? last.nextCursor : undefined),
    enabled,
    retry: false,
    refetchOnWindowFocus: false,
  });
}
