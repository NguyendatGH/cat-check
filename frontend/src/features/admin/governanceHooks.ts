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

/**
 * Hook dữ liệu cho 5 trang quản trị mới. Mọi path chép đúng `@RequestMapping` + `@*Mapping`
 * của controller backend:
 *
 * | Trang | Endpoint | Mã p8 |
 * |---|---|---|
 * | Quét | `GET /admin/users/{userId}/scans`, `GET /admin/scans/{scanId}/image`, `POST /admin/scans/{scanId}/reassign-cat` | L5, L6, L18 |
 * | Quét (chọn mèo đích) | `GET /admin/users/{userId}/cats` | L4 |
 * | Quy tắc theo dõi | `GET /admin/monitoring-rules`, `PATCH /admin/monitoring-rules/{code}` | L38, L39 |
 * | Bảo mật | `GET/POST /admin/security/incidents`, `GET/PATCH /admin/security/incidents/{id}`, `POST /admin/security/sessions/purge-all` | L59–L62 |
 * | Chính sách | `GET/POST /admin/policy-versions`, `POST /admin/policy-versions/{id}/publish` | L46–L48 |
 * | Consent | `GET /admin/privacy/consents?userId=` | L55 |
 *
 * Endpoint mang `reason` trên query (L4, L5, L6) ghi audit MỖI lần gọi, nên các query đó
 * tắt refetch tự động: một lần đổi tab trình duyệt không được sinh thêm một dòng audit.
 */

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

/** Truy vấn có ghi audit theo `reason`: chỉ chạy khi người dùng bấm, không tự chạy lại. */
const AUDITED_QUERY = {
  retry: false,
  refetchOnWindowFocus: false,
  refetchOnReconnect: false,
  staleTime: 5 * 60_000,
} as const;

// ------------------------------------------------------------------- dữ liệu quét

export interface UserScansParams {
  userId: string;
  reason: string;
  page: number;
  size: number;
  /** Chỉ chạy sau khi người dùng bấm "Tra cứu". */
  enabled: boolean;
}

/** L5 — số liệu quét của một người dùng. `reason` đi trên query vì là `GET`. */
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

/** L4 — danh sách mèo của chủ scan, để chọn mèo đích khi chuyển. */
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

/** L18 — chuyển một scan sang mèo khác. Hàng loạt thì gọi `mutateAsync` lần lượt. */
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

/**
 * L6 — ảnh gốc của scan (chỉ DPO, chỉ khi có DSAR mở). Trả `Blob` để trang tự tạo object
 * URL và thu hồi khi đóng. `cache: "no-store"` khớp header server: ảnh của người khác không
 * được nằm lại trong cache trình duyệt. KHÔNG ghi log nội dung hay URL.
 */
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
      // body không phải JSON — vẫn ném lỗi với status gốc
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

// ------------------------------------------------------------- quy tắc theo dõi

/** L38 — mọi rule kể cả rule đang tắt; mỗi dòng mang `etag` riêng. */
export function useAdminMonitoringRules(): UseQueryResult<AdminMonitoringRule[]> {
  return useQuery({
    queryKey: governanceKeys.monitoringRules,
    queryFn: async () => (await adminFetch<{ items: AdminMonitoringRule[] }>("/admin/monitoring-rules")).items,
    retry: false,
  });
}

export interface UpdateMonitoringRuleVariables {
  code: string;
  /** `etag` của dòng lúc mở form — server so với bản hiện tại, lệch thì 412. */
  etag: string;
  payload: UpdateMonitoringRulePayload;
}

/**
 * L39 — sửa rule với `If-Match`. Thành công: thay dòng trong cache bằng bản server trả (có
 * `etag` mới) để lần sửa kế tiếp dùng đúng ETag. 412: làm mới danh sách để lấy ETag mới.
 */
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
      // Body đã mang `etag`; header `ETag` là cùng giá trị — ưu tiên header nếu có.
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

// ------------------------------------------------------------------- sự cố

export interface IncidentFilters {
  severity: IncidentSeverity | "";
  unresolved: boolean;
  page: number;
  size: number;
}

/** L59 — hàng chờ hồ sơ sự cố, phân trang offset. */
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

/** Chi tiết một hồ sơ sự cố. */
export function useAdminSecurityIncident(incidentId: string | null): UseQueryResult<AdminSecurityIncident> {
  return useQuery({
    queryKey: governanceKeys.incident(incidentId ?? ""),
    queryFn: () =>
      adminFetch<AdminSecurityIncident>(`/admin/security/incidents/${encodeURIComponent(incidentId ?? "")}`),
    enabled: incidentId !== null && incidentId !== "",
    retry: false,
  });
}

/** L60 — mở hồ sơ sự cố (chỉ DPO). */
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

/** L61 — phân loại, mốc thông báo 72 giờ, kết luận (chỉ DPO). */
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

/** L62 — thu hồi TOÀN BỘ phiên của mọi người dùng (runbook R1, chỉ ADMIN_SUPER). */
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

// ------------------------------------------------------------ phiên bản chính sách

export interface PolicyVersionFilters {
  policyType: PolicyType | "";
  locale: PolicyLocale | "";
  page: number;
  size: number;
}

/** L46 — danh sách phiên bản chính sách (không kèm toàn văn). */
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

/** L47 — soạn bản nháp (chỉ DPO). */
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

/** L48 — xuất bản (chỉ DPO). 409 = đã xuất bản: làm mới danh sách. */
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

// ------------------------------------------------------------------- consent

const CONSENT_PAGE_LIMIT = 100;

/** L55 — bằng chứng consent của một người dùng, cursor keyset `(occurredAt, id)`. */
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
