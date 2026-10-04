import {
  useMutation,
  useQuery,
  useQueryClient,
  type UseMutationResult,
  type UseQueryResult,
} from "@tanstack/react-query";
import type { PhBand } from "@/entities/ph-bands";
import { adminFetch, adminQuery, adminRequest, type ApiEnvelope } from "./api";
import type {
  AdminOffsetPage,
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
  ActivationCodeStatus,
  IssueActivationBatchPayload,
  IssuedActivationBatch,
  AdminPackagePlan,
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

/**
 * Hook dữ liệu cho khu vực quản trị. CHỈ nối những endpoint đã tồn tại thật trong
 * backend — danh sách đã đối chiếu từng controller:
 *
 * | Trang | Endpoint | Mã p8 |
 * |---|---|---|
 * | Bảng màu pH | `/admin/color-charts*`, `/admin/ph-classification-bands*` | L27–L32, L36, L37 |
 * | Nội dung | `/admin/care-tips*` | L40–L45 |
 * | Tổng quan | `/admin/system-status` | — (endpoint minh hoạ của module admin) |
 * | Lưu trữ dữ liệu | `/privacy/data-inventory`, `/privacy/purposes` | C5, C1 |
 *
 * Endpoint admin còn thiếu (L1–L26, L33–L35, L38, L39, L46–L72) KHÔNG được mock ở đây —
 * trang tương ứng hiển thị trạng thái "chưa có API" kèm mã mục spec sở hữu endpoint đó.
 */

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
    status: ActivationCodeStatus | "";
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

export function useUpdateAdminMaintenance(): UseMutationResult<AdminMaintenance, Error, {
  active: boolean;
  until: string | null;
  reason: string;
}> {
  return useMutation({
    mutationFn: (payload) => adminFetch<AdminMaintenance>("/admin/system/maintenance", {
      method: "PUT", json: payload,
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

export function useRequestAdminMfaReset(): UseMutationResult<AdminMfaResetRequest, Error, { userId: string; reason: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, reason }) =>
      adminFetch<AdminMfaResetRequest>(`/admin/users/${encodeURIComponent(userId)}/mfa/totp/reset`, {
        method: "POST", json: { reason },
      }),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: adminKeys.mfaResetRequests }); },
  });
}

export function useApproveAdminMfaReset(): UseMutationResult<AdminMfaResetRequest, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId) => adminFetch<AdminMfaResetRequest>(
      `/admin/totp-reset-requests/${encodeURIComponent(requestId)}/approve`, { method: "POST" }),
    onSuccess: () => { void queryClient.invalidateQueries({ queryKey: adminKeys.mfaResetRequests }); },
  });
}

export function useGrantAdminRole(): UseMutationResult<unknown, Error, { userId: string; role: string; reason: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role, reason }) => adminFetch<unknown>(`/admin/users/${encodeURIComponent(userId)}/roles`, {
      method: "POST", json: { role, reason },
    }),
    onSuccess: (_, variables) => { void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) }); },
  });
}

export function useRevokeAdminRole(): UseMutationResult<unknown, Error, { userId: string; role: string }> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }) => adminFetch<unknown>(
      `/admin/users/${encodeURIComponent(userId)}/roles/${encodeURIComponent(role)}`, { method: "DELETE" }),
    onSuccess: (_, variables) => { void queryClient.invalidateQueries({ queryKey: adminKeys.userDetail(variables.userId) }); },
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
    mutationFn: (code) => adminFetch<AdminRetentionDryRun>(
      `/admin/privacy/retention-policies/${encodeURIComponent(code)}/dry-run`, { method: "POST" }),
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
    mutationFn: (payload) => adminFetch<AdminDsarRequest>("/admin/privacy/requests", {
      method: "POST", json: payload,
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
    mutationFn: ({ requestId, ...payload }) => adminFetch<AdminDsarRequest>(
      `/admin/privacy/requests/${encodeURIComponent(requestId)}/transition`, {
        method: "POST", json: payload,
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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
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
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
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
  status: ActivationCodeStatus | "";
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
  return useMutation({ mutationFn: ({ batchId, reason }) => downloadAdminActivationBatchCsv(batchId, reason) });
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

// ------------------------------------------------------------------ hệ thống

/** `GET /admin/system-status` — chỉ báo DB còn kết nối, KHÔNG thay `/actuator/health`. */
export function useAdminSystemStatus(): UseQueryResult<AdminSystemStatus> {
  return useQuery({
    queryKey: adminKeys.systemStatus,
    queryFn: () => adminFetch<AdminSystemStatus>("/admin/system-status"),
    retry: false,
    refetchInterval: 60_000,
  });
}

// --------------------------------------------------------------- bảng màu pH

/** L27 — danh sách bảng màu mọi trạng thái, phân trang offset. */
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

/** L29 — chi tiết + toàn bộ điểm + `ETag` (nguồn `If-Match` cho L30/L31). */
export function useAdminColorChart(chartId: string | null): UseQueryResult<ApiEnvelope<ColorChartDetail>> {
  return useQuery({
    queryKey: adminKeys.colorChart(chartId ?? ""),
    queryFn: () => adminRequest<ColorChartDetail>(`/admin/color-charts/${chartId ?? ""}`),
    enabled: chartId !== null && chartId !== "",
    retry: false,
  });
}

/** L28 — tạo bản DRAFT. */
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
  /** p17 AD10 — bắt buộc, gửi kèm body (xem ghi chú ở `api.ts`). */
  reason: string;
}

/** L30 — sửa metadata bản DRAFT. `If-Match` bắt buộc. */
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

/** L31 — thay TOÀN BỘ danh sách mức pH. `If-Match` bắt buộc. */
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

/** L32 — DRAFT ⇒ ACTIVE; bản cũ ⇒ ARCHIVED. Thiếu điểm ⇒ 422 COLOR_CHART_INCOMPLETE. */
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

/** L36 — 6 dải phân loại + `ETag`. Nhãn đã resolve theo `Accept-Language`. */
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

/**
 * L37 — sửa nhãn/màu/icon của một dải.
 *
 * CỐ Ý không cho sửa `minPh`/`maxPh` ở màn này: p8 L37 ghi "đổi ngưỡng ⇒ step-up", mà
 * lớp step-up (`S1:TOTP`) chưa được nối (javadoc `AdminColorChartController`). Mở ô nhập
 * ngưỡng khi chưa có step-up là mở đúng thứ mà spec bắt phải khoá.
 */
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

// ----------------------------------------------------------------- nội dung

/** L40 — danh sách nội dung mọi trạng thái. */
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

/** L41 — tạo bản nháp. */
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

/** L42 — sửa bản nháp. Bài PUBLISHED ⇒ 409 CONTENT_NOT_EDITABLE. */
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

/** L43 — gửi duyệt. */
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
  /** `PublishCareTipRequest.reason` — `@NotBlank @Size(min = 10)`, server CÓ lưu. */
  reason: string;
}

/**
 * L44 — công bố. 422 CONTENT_SOURCE_REQUIRED nếu `claimType` khác NONE mà thiếu nguồn;
 * 409 CONTENT_SELF_APPROVAL_FORBIDDEN nếu người soạn tự duyệt bài của mình.
 */
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

/** L45 — gỡ khỏi hiển thị. */
export function useArchiveCareTip(): UseMutationResult<CareTipDetail, Error, string> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (tipId) => adminFetch<CareTipDetail>(`/admin/care-tips/${tipId}/archive`, { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: adminKeys.all });
    },
  });
}

// ------------------------------------------------------------ quyền riêng tư

/**
 * C5 `GET /privacy/data-inventory` — danh mục `data_inventory_item` TOÀN CỤC (nhóm dữ
 * liệu, căn cứ pháp lý, mã chính sách lưu trữ). KHÔNG phải dữ liệu của một user nào, nên
 * dùng được làm bảng tra chỉ-đọc cho DPO trong lúc chờ L56/L57.
 */
export function useAdminDataInventory(): UseQueryResult<PrivacyPage<DataInventoryItem>> {
  return useQuery({
    queryKey: adminKeys.dataInventory,
    queryFn: () => adminFetch<PrivacyPage<DataInventoryItem>>("/privacy/data-inventory"),
    retry: false,
  });
}

/** C1 `GET /privacy/purposes` — danh mục `consent_purpose` đã resolve theo locale. */
export function useAdminConsentPurposes(): UseQueryResult<PrivacyPage<ConsentPurposeItem>> {
  return useQuery({
    queryKey: adminKeys.consentPurposes,
    queryFn: () => adminFetch<PrivacyPage<ConsentPurposeItem>>("/privacy/purposes"),
    retry: false,
  });
}
