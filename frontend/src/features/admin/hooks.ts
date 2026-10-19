import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { PhBand } from "@/entities/ph-bands";
import {
  adminFetch,
  adminQuery,
  adminRequest,
  changeAdminOrderStatus,
  listAdminOrders,
  type ApiEnvelope,
} from "./api";
import type {
  AdminOffsetPage,
  AdminOrder,
  AdminSystemStatus,
  CareTipDetail,
  CareTipStatus,
  ChartStatus,
  ColorChartDetail,
  ColorChartPointInput,
  ColorChartSummary,
  ConsentPurposeItem,
  CreateCareTipPayload,
  CreateColorChartPayload,
  DataInventoryItem,
  PrivacyPage,
  UpdateCareTipPayload,
  UpdateColorChartPayload,
  UpdatePhBandPayload,
  CommunityReport,
  CommunityReportAction,
  ActivationBatch,
  ActivationCodeAdmin,
  ActivationCodeEffectiveStatus,
  IssueActivationBatchPayload,
  IssuedActivationBatch,
  AdminPackagePlan,
  AdminProduct,
  AdminProductPayload,
  AdminProductStatus,
  UpdateAdminPackagePlanPayload,
  AdminJobRun,
  AdminOutboxEntry,
  AdminAuditLog,
  AdminMetrics,
  AdminDsarRequest,
  AdminUserStatus,
  AdminUserSummary,
  AdminUserDetail,
  AdminUnmaskResponse,
  AdminSessionListResponse,
  AdminRetentionPolicy,
  AdminRetentionDryRun,
  AdminMfaResetRequest,
  AdminMaintenance,
} from "./types";
import { downloadAdminActivationBatchCsv } from "./api";

export const adminKeys = {
  all: ["admin"] as const,
  systemStatus: ["admin", "system-status"] as const,
  colorCharts: (status: ChartStatus | "", page: number, size: number) =>
    ["admin", "color-charts", status, page, size] as const,
  colorChart: (chartId: string) => ["admin", "color-chart", chartId] as const,
  phBands: ["admin", "ph-bands"] as const,
  careTips: (locale: string, status: CareTipStatus | "", page: number, size: number) =>
    ["admin", "care-tips", locale, status, page, size] as const,
  dataInventory: ["admin", "data-inventory"] as const,
  consentPurposes: ["admin", "consent-purposes"] as const,
  communityReports: (status: string, page: number, size: number) =>
    ["admin", "community-reports", status, page, size] as const,
  activationCodes: (filters: {
    prefix: string;
    packageCode: string;
    status: ActivationCodeEffectiveStatus | "";
    batchId: string;
    page: number;
    size: number;
  }) => ["admin", "activation-codes", filters] as const,
  activationBatches: (page: number, size: number) => ["admin", "activation-batches", page, size] as const,
  packagePlans: (includeInactive: boolean) => ["admin", "package-plans", includeInactive] as const,
  jobRuns: (filters: { jobName: string; status: string; page: number; size: number }) =>
    ["admin", "job-runs", filters] as const,
  outbox: (filters: { channel: string; status: string; page: number; size: number }) =>
    ["admin", "outbox", filters] as const,
  auditLogs: (filters: { action: string; result: string; actorType: string; page: number; size: number }) =>
    ["admin", "audit-logs", filters] as const,
  metrics: ["admin", "metrics"] as const,
  privacyRequests: (filters: { requestType: string; status: string; page: number; size: number }) =>
    ["admin", "privacy-requests", filters] as const,
  privacyRequest: (requestId: string) => ["admin", "privacy-request", requestId] as const,
  retentionPolicies: ["admin", "retention-policies"] as const,
  users: (filters: { status: AdminUserStatus | ""; email: string; packageCode: string; page: number; size: number }) =>
    ["admin", "users", filters] as const,
  userDetail: (userId: string) => ["admin", "user-detail", userId] as const,
  userSessions: (userId: string) => ["admin", "user-sessions", userId] as const,
  mfaResetRequests: ["admin", "mfa-reset-requests"] as const,
  maintenance: ["admin", "maintenance"] as const,
};

export function useUpdateAdminMaintenance(): UseMutationResult<
  AdminMaintenance,
  Error,
  {
    active: boolean;
    until: string | null;
    reason: string;
  }
> {
  return useMutation({
    mutationFn: (payload) =>
      adminFetch<AdminMaintenance>("/admin/system/maintenance", {
        method: "PUT",
        json: payload,
      }),
  });
}

export function useAdminMfaResetRequests(): UseQueryResult<AdminMfaResetRequest[]> {
  return useQuery({
    queryKey: adminKeys.mfaResetRequests,
    queryFn: () => adminFetch<AdminMfaResetRequest[]>("/admin/totp-reset-requests"),
    retry: false,
  });
}

export function useRequestAdminMfaReset(): UseMutationResult<
  AdminMfaResetRequest,
  Error,
  { userId: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch<AdminMfaResetRequest>(`/admin/users/${encodeURIComponent(userId)}/mfa/totp/reset`, {
        method: "POST",
        json: { reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.mfaResetRequests });
    },
  });
}

export function useApproveAdminMfaReset(): UseMutationResult<AdminMfaResetRequest, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId) =>
      adminFetch<AdminMfaResetRequest>(`/admin/totp-reset-requests/${encodeURIComponent(requestId)}/approve`, {
        method: "POST",
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.mfaResetRequests });
    },
  });
}

export function useGrantAdminRole(): UseMutationResult<
  unknown,
  Error,
  { userId: string; role: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role, reason }) =>
      adminFetch<unknown>(`/admin/users/${encodeURIComponent(userId)}/roles`, {
        method: "POST",
        json: { role, reason },
      }),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) });
    },
  });
}

export function useRevokeAdminRole(): UseMutationResult<unknown, Error, { userId: string; role: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }) =>
      adminFetch<unknown>(`/admin/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`, {
        method: "DELETE",
      }),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) });
    },
  });
}

export function useAdminRetentionPolicies(): UseQueryResult<AdminRetentionPolicy[]> {
  return useQuery({
    queryKey: adminKeys.retentionPolicies,
    queryFn: () => adminFetch<AdminRetentionPolicy[]>("/admin/privacy/retention-policies"),
    retry: false,
  });
}

export function useDryRunAdminRetentionPolicy(): UseMutationResult<AdminRetentionDryRun, Error, string> {
  return useMutation({
    mutationFn: (code) =>
      adminFetch<AdminRetentionDryRun>(`/admin/privacy/retention-policies/${encodeURIComponent(code)}/dry-run`, {
        method: "POST",
      }),
  });
}

export interface UpdateAdminRetentionPolicyPayload {
  dataInventoryCode: string | null;
  targetTable: string;
  retentionDays: number | null;
  anchorColumn: string;
  actionOnExpiry: AdminRetentionPolicy["actionOnExpiry"];
  jobName: string | null;
  safetyThresholdPercent: number;
  enabled: boolean;
  legalBasis: string | null;
}

export function useUpdateAdminRetentionPolicy(): UseMutationResult<
  AdminRetentionPolicy,
  Error,
  { code: string; payload: UpdateAdminRetentionPolicyPayload }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, payload }) =>
      adminFetch<AdminRetentionPolicy>(`/admin/privacy/retention-policies/${encodeURIComponent(code)}`, {
        method: "PATCH",
        json: payload,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.retentionPolicies });
      void queryClient.invalidateQueries({ queryKey: adminKeys.dataInventory });
    },
  });
}

export function useAdminMetrics(): UseQueryResult<AdminMetrics> {
  return useQuery({
    queryKey: adminKeys.metrics,
    queryFn: () => adminFetch<AdminMetrics>("/admin/metrics/dashboard"),
    retry: false,
    staleTime: 30_000,
    // dashboard.metricsDescription hứa "cập nhật tự động mỗi 30 giây".
    refetchInterval: 30_000,
  });
}

export function useAdminPrivacyRequests(filters: {
  requestType: string;
  status: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<AdminDsarRequest>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.privacyRequests({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<AdminDsarRequest>>(
        `/admin/privacy/requests${adminQuery({ requestType: filters.requestType || undefined, status: filters.status || undefined, page: filters.page, size })}`,
      ),
    retry: false,
  });
}

export function useAdminPrivacyRequest(requestId: string | undefined): UseQueryResult<AdminDsarRequest> {
  return useQuery({
    queryKey: adminKeys.privacyRequest(requestId ?? ""),
    queryFn: () => adminFetch<AdminDsarRequest>(`/admin/privacy/requests/${encodeURIComponent(requestId ?? "")}`),
    enabled: Boolean(requestId),
    retry: false,
  });
}

export function useCreateAdminPrivacyRequest(): UseMutationResult<
  AdminDsarRequest,
  Error,
  { userId: string; requestType: string; channel: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      adminFetch<AdminDsarRequest>("/admin/privacy/requests", {
        method: "POST",
        json: payload,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "privacy-requests"] });
    },
  });
}

export function useTransitionAdminPrivacyRequest(): UseMutationResult<
  AdminDsarRequest,
  Error,
  { requestId: string; action: string; reason?: string; extendedTo?: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, ...payload }) =>
      adminFetch<AdminDsarRequest>(`/admin/privacy/requests/${encodeURIComponent(requestId)}/transition`, {
        method: "POST",
        json: payload,
      }),
    onSuccess: (data) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "privacy-requests"] });
      void queryClient.invalidateQueries({ queryKey: adminKeys.privacyRequest(data.id) });
    },
  });
}

export function useAdminUsers(filters: {
  status: AdminUserStatus | "";
  email: string;
  packageCode: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<AdminUserSummary>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.users({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<AdminUserSummary>>(
        `/admin/users${adminQuery({ status: filters.status || undefined, email: filters.email || undefined, packageCode: filters.packageCode || undefined, page: filters.page, size })}`,
      ),
    retry: false,
  });
}

export interface AdminUserStatusMutationVariables {
  userId: string;
  reason: string;
}

export function useLockAdminUser(): UseMutationResult<
  { userId: string; status: AdminUserStatus; revokedSessions: number },
  Error,
  AdminUserStatusMutationVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch(`/admin/users/${encodeURIComponent(userId)}/lock`, { method: "POST", json: { reason } }),
    onSuccess: (_data, { userId }) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(userId) });
      void queryClient.invalidateQueries({ queryKey: adminKeys.userSessions(userId) });
    },
  });
}

export function useUnlockAdminUser(): UseMutationResult<
  { userId: string; status: AdminUserStatus; revokedSessions: number },
  Error,
  AdminUserStatusMutationVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch(`/admin/users/${encodeURIComponent(userId)}/unlock`, { method: "POST", json: { reason } }),
    onSuccess: (_data, { userId }) => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(userId) });
      void queryClient.invalidateQueries({ queryKey: adminKeys.userSessions(userId) });
    },
  });
}

export function useAdminUserDetail(userId: string | undefined): UseQueryResult<AdminUserDetail> {
  return useQuery({
    queryKey: adminKeys.userDetail(userId ?? ""),
    queryFn: () => adminFetch<AdminUserDetail>(`/admin/users/${encodeURIComponent(userId ?? "")}`),
    enabled: userId !== undefined && userId.length > 0,
    retry: false,
  });
}

export function useUnmaskAdminUser(): UseMutationResult<
  AdminUnmaskResponse,
  Error,
  { userId: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch<AdminUnmaskResponse>(`/admin/users/${encodeURIComponent(userId)}/unmask`, {
        method: "POST",
        json: { reason },
      }),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) });
    },
  });
}

export function useAdminUserSessions(userId: string | undefined): UseQueryResult<AdminSessionListResponse> {
  return useQuery({
    queryKey: adminKeys.userSessions(userId ?? ""),
    queryFn: () => adminFetch<AdminSessionListResponse>(`/admin/users/${encodeURIComponent(userId ?? "")}/sessions`),
    enabled: userId !== undefined && userId.length > 0,
    retry: false,
  });
}

export function useRevokeAdminUserSessions(): UseMutationResult<
  AdminSessionListResponse,
  Error,
  { userId: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch<AdminSessionListResponse>(
        `/admin/users/${encodeURIComponent(userId)}/sessions${adminQuery({ reason })}`,
        { method: "DELETE" },
      ),
    onSuccess: (_, variables) => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) });
      void queryClient.invalidateQueries({ queryKey: adminKeys.userSessions(variables.userId) });
    },
  });
}

export function useAdminJobRuns(filters: {
  jobName: string;
  status: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<AdminJobRun>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.jobRuns({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<AdminJobRun>>(
        `/admin/jobs/runs${adminQuery({ jobName: filters.jobName || undefined, status: filters.status || undefined, page: filters.page, size })}`,
      ),
    retry: false,
  });
}

export function useAdminOutbox(filters: {
  channel: string;
  status: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<AdminOutboxEntry>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.outbox({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<AdminOutboxEntry>>(
        `/admin/notifications/outbox${adminQuery({ channel: filters.channel || undefined, status: filters.status || undefined, page: filters.page, size })}`,
      ),
    retry: false,
  });
}

export function useAdminAuditLogs(filters: {
  action: string;
  result: string;
  actorType: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<AdminAuditLog>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.auditLogs({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<AdminAuditLog>>(
        `/admin/audit-logs${adminQuery({
          action: filters.action || undefined,
          result: filters.result || undefined,
          actorType: filters.actorType || undefined,
          page: filters.page,
          size,
        })}`,
      ),
    retry: false,
  });
}

export function useAdminPackagePlans(includeInactive = true): UseQueryResult<AdminPackagePlan[]> {
  return useQuery({
    queryKey: adminKeys.packagePlans(includeInactive),
    queryFn: () =>
      adminFetch<AdminPackagePlan[]>(`/admin/package-plans${adminQuery({ includeInactive: String(includeInactive) })}`),
    retry: false,
  });
}

export interface UpdateAdminPackagePlanVariables {
  code: string;
  etag: string;
  payload: UpdateAdminPackagePlanPayload;
}

export function useUpdateAdminPackagePlan(): UseMutationResult<
  AdminPackagePlan,
  Error,
  UpdateAdminPackagePlanVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, etag, payload }) =>
      adminFetch<AdminPackagePlan>(`/admin/package-plans/${encodeURIComponent(code)}`, {
        method: "PATCH",
        ifMatch: etag,
        json: payload,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "package-plans"] });
    },
  });
}

export function useAdminActivationCodes(filters: {
  prefix: string;
  packageCode: string;
  status: ActivationCodeEffectiveStatus | "";
  batchId: string;
  page: number;
  size?: number;
}): UseQueryResult<AdminOffsetPage<ActivationCodeAdmin>> {
  const size = filters.size ?? 20;
  return useQuery({
    queryKey: adminKeys.activationCodes({ ...filters, size }),
    queryFn: () =>
      adminFetch<AdminOffsetPage<ActivationCodeAdmin>>(
        `/admin/activation-codes${adminQuery({
          prefix: filters.prefix || undefined,
          packageCode: filters.packageCode || undefined,
          status: filters.status || undefined,
          batchId: filters.batchId || undefined,
          page: filters.page,
          size,
        })}`,
      ),
    retry: false,
  });
}

export function useAdminActivationBatches(page: number, size = 20): UseQueryResult<AdminOffsetPage<ActivationBatch>> {
  return useQuery({
    queryKey: adminKeys.activationBatches(page, size),
    queryFn: () =>
      adminFetch<AdminOffsetPage<ActivationBatch>>(`/admin/activation-codes/batches${adminQuery({ page, size })}`),
    retry: false,
  });
}

export function useIssueActivationBatch(): UseMutationResult<
  IssuedActivationBatch,
  Error,
  IssueActivationBatchPayload
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) =>
      adminFetch<IssuedActivationBatch>("/admin/activation-codes/batch", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-codes"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-batches"] });
    },
  });
}

export function useVoidActivationCode(): UseMutationResult<
  ActivationCodeAdmin,
  Error,
  { codeId: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ codeId, reason }) =>
      adminFetch<ActivationCodeAdmin>(`/admin/activation-codes/${encodeURIComponent(codeId)}/void`, {
        method: "POST",
        json: { reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-codes"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-batches"] });
    },
  });
}

export function useVoidActivationBatch(): UseMutationResult<
  ActivationBatch,
  Error,
  { batchId: string; reason: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ batchId, reason }) =>
      adminFetch<ActivationBatch>(`/admin/activation-codes/batches/${encodeURIComponent(batchId)}/void`, {
        method: "POST",
        json: { reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-codes"] });
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-batches"] });
    },
  });
}

export function useDownloadActivationBatchCsv(): UseMutationResult<Blob, Error, { batchId: string; reason: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ batchId, reason }) => downloadAdminActivationBatchCsv(batchId, reason),
    // CSV chỉ tải được một lần (L22): làm mới để `csvAvailable` phản ánh trạng thái server.
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "activation-batches"] });
    },
  });
}

export function useAdminCommunityReports(
  status: string,
  page: number,
  size = 20,
): UseQueryResult<AdminOffsetPage<CommunityReport>> {
  return useQuery({
    queryKey: adminKeys.communityReports(status, page, size),
    queryFn: () =>
      adminFetch<AdminOffsetPage<CommunityReport>>(`/admin/community/reports${adminQuery({ status, page, size })}`),
    retry: false,
  });
}

export interface ModerateCommunityReportVariables {
  reportId: string;
  action: CommunityReportAction;
  reason: string;
}

export function useModerateCommunityReport(): UseMutationResult<
  CommunityReport,
  Error,
  ModerateCommunityReportVariables
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ reportId, action, reason }) =>
      adminFetch<CommunityReport>(`/admin/community/reports/${reportId}/resolve`, {
        method: "POST",
        json: { action, reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.communityReports("", 0, 20).slice(0, 2) });
    },
  });
}

export function useAdminSystemStatus(): UseQueryResult<AdminSystemStatus> {
  return useQuery({
    queryKey: adminKeys.systemStatus,
    queryFn: () => adminFetch<AdminSystemStatus>("/admin/system-status"),
    retry: false,
    refetchInterval: 60_000,
  });
}

export function useAdminColorCharts(
  status: ChartStatus | "",
  page: number,
  size = 20,
): UseQueryResult<AdminOffsetPage<ColorChartSummary>> {
  return useQuery({
    queryKey: adminKeys.colorCharts(status, page, size),
    queryFn: () =>
      adminFetch<AdminOffsetPage<ColorChartSummary>>(
        `/admin/color-charts${adminQuery({ status: status === "" ? undefined : status, page, size })}`,
      ),
    retry: false,
  });
}

export function useAdminColorChart(chartId: string | null): UseQueryResult<ApiEnvelope<ColorChartDetail>> {
  return useQuery({
    queryKey: adminKeys.colorChart(chartId ?? ""),
    queryFn: () => adminRequest<ColorChartDetail>(`/admin/color-charts/${chartId ?? ""}`),
    enabled: chartId !== null && chartId !== "",
    retry: false,
  });
}

export function useCreateColorChart(): UseMutationResult<ColorChartDetail, Error, CreateColorChartPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => adminFetch<ColorChartDetail>("/admin/color-charts", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface UpdateColorChartVariables {
  chartId: string;
  etag: string | null;
  payload: UpdateColorChartPayload;
  reason: string;
}

export function useUpdateColorChart(): UseMutationResult<ColorChartDetail, Error, UpdateColorChartVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ chartId, etag, payload, reason }) =>
      adminFetch<ColorChartDetail>(`/admin/color-charts/${chartId}`, {
        method: "PATCH",
        ifMatch: etag,
        json: { ...payload, reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface ReplacePointsVariables {
  chartId: string;
  etag: string | null;
  points: ColorChartPointInput[];
  reason: string;
}

export function useReplaceChartPoints(): UseMutationResult<ColorChartDetail, Error, ReplacePointsVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ chartId, etag, points, reason }) =>
      adminFetch<ColorChartDetail>(`/admin/color-charts/${chartId}/points`, {
        method: "PUT",
        ifMatch: etag,
        json: { points, reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface PublishChartVariables {
  chartId: string;
  reason: string;
}

export function usePublishColorChart(): UseMutationResult<ColorChartDetail, Error, PublishChartVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ chartId, reason }) =>
      adminFetch<ColorChartDetail>(`/admin/color-charts/${chartId}/publish`, { method: "POST", json: { reason } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface CloneChartVariables {
  source: ColorChartDetail;
  reason: string;
}

/**
 * Tạo bản nháp mới (cùng mã, phiên bản kế tiếp) rồi chép toàn bộ mức màu của bảng nguồn.
 * Máy chủ chưa có endpoint clone riêng nên ghép từ L28 (tạo) + L29 (lấy ETag) + L31 (thay mức).
 */
export function useCloneColorChart(): UseMutationResult<ColorChartDetail, Error, CloneChartVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({ source, reason }) => {
      const created = await adminFetch<ColorChartDetail>("/admin/color-charts", {
        method: "POST",
        json: {
          code: source.code,
          name: source.name,
          productLine: source.productLine ?? "STANDARD",
          ...(source.productionBatch !== null && source.productionBatch.trim() !== ""
            ? { productionBatch: source.productionBatch }
            : {}),
        } satisfies CreateColorChartPayload,
      });
      if (source.points.length === 0) {
        return created;
      }
      const fresh = await adminRequest<ColorChartDetail>(`/admin/color-charts/${created.id}`);
      const points: ColorChartPointInput[] = [...source.points]
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((point) => ({
          phValue: point.phValue,
          labL: point.labL,
          labA: point.labA,
          labB: point.labB,
          toleranceDeltaE: point.toleranceDeltaE,
          hexSrgb: point.hexSrgb,
          displayHex: point.displayHex,
          displayNameVi: point.displayNameVi,
          displayNameEn: point.displayNameEn,
        }));
      return adminFetch<ColorChartDetail>(`/admin/color-charts/${created.id}/points`, {
        method: "PUT",
        ifMatch: fresh.etag,
        json: { points, reason },
      });
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export function useAdminPhBands(): UseQueryResult<ApiEnvelope<PhBand[]>> {
  return useQuery({
    queryKey: adminKeys.phBands,
    queryFn: () => adminRequest<PhBand[]>("/admin/ph-classification-bands"),
    retry: false,
  });
}

export interface UpdatePhBandVariables {
  code: string;
  etag: string | null;
  payload: UpdatePhBandPayload;
  reason: string;
}

export function useUpdatePhBand(): UseMutationResult<PhBand, Error, UpdatePhBandVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ code, etag, payload, reason }) =>
      adminFetch<PhBand>(`/admin/ph-classification-bands/${code}`, {
        method: "PATCH",
        ifMatch: etag,
        json: { ...payload, reason },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.phBands });
    },
  });
}

export function useAdminCareTips(
  locale: string,
  status: CareTipStatus | "",
  page: number,
  size = 20,
): UseQueryResult<AdminOffsetPage<CareTipDetail>> {
  return useQuery({
    queryKey: adminKeys.careTips(locale, status, page, size),
    queryFn: () =>
      adminFetch<AdminOffsetPage<CareTipDetail>>(
        `/admin/care-tips${adminQuery({
          locale: locale === "" ? undefined : locale,
          status: status === "" ? undefined : status,
          page,
          size,
        })}`,
      ),
    retry: false,
  });
}

export function useCreateCareTip(): UseMutationResult<CareTipDetail, Error, CreateCareTipPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => adminFetch<CareTipDetail>("/admin/care-tips", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface UpdateCareTipVariables {
  tipId: string;
  payload: UpdateCareTipPayload;
}

export function useUpdateCareTip(): UseMutationResult<CareTipDetail, Error, UpdateCareTipVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tipId, payload }) =>
      adminFetch<CareTipDetail>(`/admin/care-tips/${tipId}`, { method: "PATCH", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export function useSubmitCareTipReview(): UseMutationResult<CareTipDetail, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tipId) => adminFetch<CareTipDetail>(`/admin/care-tips/${tipId}/submit-review`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export interface PublishCareTipVariables {
  tipId: string;
  reason: string;
}

export function usePublishCareTip(): UseMutationResult<CareTipDetail, Error, PublishCareTipVariables> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ tipId, reason }) =>
      adminFetch<CareTipDetail>(`/admin/care-tips/${tipId}/publish`, { method: "POST", json: { reason } }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export function useArchiveCareTip(): UseMutationResult<CareTipDetail, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tipId) => adminFetch<CareTipDetail>(`/admin/care-tips/${tipId}/archive`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

export function useAdminDataInventory(): UseQueryResult<PrivacyPage<DataInventoryItem>> {
  return useQuery({
    queryKey: adminKeys.dataInventory,
    queryFn: () => adminFetch<PrivacyPage<DataInventoryItem>>("/privacy/data-inventory"),
    retry: false,
  });
}

export function useAdminConsentPurposes(): UseQueryResult<PrivacyPage<ConsentPurposeItem>> {
  return useQuery({
    queryKey: adminKeys.consentPurposes,
    queryFn: () => adminFetch<PrivacyPage<ConsentPurposeItem>>("/privacy/purposes"),
    retry: false,
  });
}

const ADMIN_PRODUCTS_KEY = ["admin", "shop-products"] as const;

export function useAdminProducts(): UseQueryResult<AdminProduct[]> {
  return useQuery({
    queryKey: ADMIN_PRODUCTS_KEY,
    queryFn: () => adminFetch<AdminProduct[]>("/admin/shop/products"),
    retry: false,
  });
}

export function useCreateAdminProduct(): UseMutationResult<AdminProduct, Error, AdminProductPayload> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload) => adminFetch<AdminProduct>("/admin/shop/products", { method: "POST", json: payload }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_PRODUCTS_KEY });
    },
  });
}

export function useUpdateAdminProduct(): UseMutationResult<
  AdminProduct,
  Error,
  { id: string; payload: AdminProductPayload; etag: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload, etag }) =>
      adminFetch<AdminProduct>(`/admin/shop/products/${encodeURIComponent(id)}`, {
        method: "PUT",
        ifMatch: etag,
        json: payload,
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_PRODUCTS_KEY });
    },
  });
}

export function useSetAdminProductStatus(): UseMutationResult<
  AdminProduct,
  Error,
  { id: string; status: AdminProductStatus }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status }) =>
      adminFetch<AdminProduct>(`/admin/shop/products/${encodeURIComponent(id)}/status`, {
        method: "PATCH",
        json: { status },
      }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_PRODUCTS_KEY });
    },
  });
}

const ADMIN_ORDERS_KEY = ["admin", "shop-orders"] as const;

export function useAdminOrders(status: string | undefined, page: number): UseQueryResult<AdminOrder[]> {
  return useQuery({
    queryKey: [...ADMIN_ORDERS_KEY, status ?? "", page],
    queryFn: () => listAdminOrders(status, page),
    retry: false,
  });
}

export function useChangeAdminOrderStatus(): UseMutationResult<
  AdminOrder,
  Error,
  { id: string; status: string; reason?: string }
> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, status, reason }) => changeAdminOrderStatus(id, status, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ADMIN_ORDERS_KEY });
    },
  });
}

/** Số đếm toàn bộ dữ liệu cho KPI admin (không riêng trang đang xem). */
export interface AdminStatusSummary {
  byStatus: Record<string, number>;
}

export interface AdminPrivacyRequestSummary {
  open: number;
  overdue: number;
  extended: number;
}

export function useAdminUserStatusSummary(): UseQueryResult<AdminStatusSummary> {
  return useQuery({
    // Tiền tố ["admin", "users"] để lock/unlock tự làm mới số đếm.
    queryKey: ["admin", "users", "summary"] as const,
    queryFn: () => adminFetch<AdminStatusSummary>("/admin/users/summary"),
    retry: false,
  });
}

export function useAdminActivationCodeSummary(filters: {
  prefix: string;
  packageCode: string;
  batchId: string;
}): UseQueryResult<AdminStatusSummary> {
  return useQuery({
    queryKey: ["admin", "activation-codes", "summary", filters] as const,
    queryFn: () =>
      adminFetch<AdminStatusSummary>(
        `/admin/activation-codes/summary${adminQuery({
          prefix: filters.prefix || undefined,
          packageCode: filters.packageCode || undefined,
          batchId: filters.batchId || undefined,
        })}`,
      ),
    retry: false,
  });
}

export function useAdminPrivacyRequestSummary(requestType: string): UseQueryResult<AdminPrivacyRequestSummary> {
  return useQuery({
    queryKey: ["admin", "privacy-requests", "summary", requestType] as const,
    queryFn: () =>
      adminFetch<AdminPrivacyRequestSummary>(
        `/admin/privacy/requests/summary${adminQuery({ requestType: requestType || undefined })}`,
      ),
    retry: false,
  });
}
