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
} from "./types";

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
};

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
