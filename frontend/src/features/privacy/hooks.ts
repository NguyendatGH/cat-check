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

/**
 * Hooks react-query của Trung tâm quyền riêng tư — gọi thẳng `PrivacyController`
 * (C1–C15, p8 §8.4.3) và `AuthController` A4/A5/A12 cho bước step-up re-auth.
 */

export const PRIVACY_KEYS = {
  purposes: ["privacy", "purposes"] as const,
  consents: ["privacy", "consents"] as const,
  consentHistory: ["privacy", "consents", "history"] as const,
  dataInventory: ["privacy", "data-inventory"] as const,
  requests: ["privacy", "requests"] as const,
  exportStatus: (publicRef: string) => ["privacy", "export", publicRef] as const,
  accessLog: ["privacy", "access-log"] as const,
};

/** Ân hạn xoá tài khoản: 7 ngày (TD-05, p15 §15.4.6, `DsarService.DELETION_GRACE_DAYS`). */
export const DELETION_GRACE_DAYS = 7;

/** C1 — danh mục mục đích xử lý. Cấu hình, đổi hiếm ⇒ cache dài. */
export function useConsentPurposes(): UseQueryResult<PurposeView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.purposes,
    queryFn: async () => (await apiFetch<PageView<PurposeView>>("/privacy/purposes")).items,
    staleTime: 10 * 60 * 1000,
  });
}

/**
 * C2 — trạng thái hiện hành từng purpose, đọc từ view `consent_current` của server.
 * KHÔNG suy ra từ lịch sử ở client: lệch ở đây là lệch bằng chứng pháp lý (p4 B2).
 */
export function useCurrentConsents(): UseQueryResult<ConsentStateView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.consents,
    queryFn: async () => (await apiFetch<PageView<ConsentStateView>>("/privacy/consents")).items,
  });
}

/**
 * C3 — cấp/rút consent. Mỗi lần gọi là **INSERT** một dòng `consent_record` mới
 * (append-only, bất biến I16) — không bao giờ sửa dòng cũ. Vì vậy sau khi ghi phải làm mới
 * cả trạng thái hiện hành lẫn sổ lịch sử.
 */
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

/** C4 — sổ bằng chứng consent của tôi, mới nhất trước. */
export function useConsentHistory(limit = 50): UseQueryResult<ConsentHistoryView[]> {
  return useQuery({
    queryKey: [...PRIVACY_KEYS.consentHistory, limit],
    queryFn: async () =>
      (await apiFetch<PageView<ConsentHistoryView>>(`/privacy/consents/history?limit=${String(limit)}`)).items,
  });
}

/** C5 — "Dữ liệu CatCheck đang giữ về bạn", render động từ `data_inventory_item`. */
export function useDataInventory(): UseQueryResult<DataInventoryView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.dataInventory,
    queryFn: async () => (await apiFetch<PageView<DataInventoryView>>("/privacy/data-inventory")).items,
    staleTime: 10 * 60 * 1000,
  });
}

/** C14 — danh sách yêu cầu DSAR của tôi (mới nhất trước). */
export function useDsarRequests(limit = 50): UseQueryResult<DsarRequestView[]> {
  return useQuery({
    queryKey: [...PRIVACY_KEYS.requests, limit],
    queryFn: async () => (await apiFetch<PageView<DsarRequestView>>(`/privacy/requests?limit=${String(limit)}`)).items,
  });
}

/** C7 — trạng thái một gói xuất (thêm `resultExpiresAt` mà C14 không trả). */
export function useExportStatus(publicRef: string | null): UseQueryResult<ExportStatusView> {
  return useQuery({
    queryKey: PRIVACY_KEYS.exportStatus(publicRef ?? ""),
    queryFn: () => apiFetch<ExportStatusView>(`/privacy/export/${publicRef ?? ""}`),
    enabled: publicRef !== null,
    refetchInterval: (query) => ["COMPLETED", "REJECTED"].includes(query.state.data?.status ?? "") ? false : 5_000,
  });
}

/** C6 — yêu cầu xuất dữ liệu. Cần step-up; tối đa 1 lần/24 giờ. */
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

/**
 * C8 — tải gói ZIP bất đồng bộ (link một lần, 72 giờ).
 */
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

/** C9 — yêu cầu xoá tài khoản. Cần step-up; đặt `deletion_scheduled_at = now() + 7 ngày`. */
export function useRequestAccountDeletion(): UseMutationResult<ExportStatusView, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<ExportStatusView>("/privacy/account-deletion", { method: "POST" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

/** C10 — huỷ yêu cầu xoá trong 7 ngày ân hạn. */
export function useCancelAccountDeletion(): UseMutationResult<undefined, Error, void> {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/privacy/account-deletion", { method: "DELETE" }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: PRIVACY_KEYS.requests });
    },
  });
}

/** C11/C12 — bật/tắt hạn chế xử lý (`app_user.processing_restricted_at`). */
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

/** C13 — mở yêu cầu DSAR không tự phục vụ được. Cần step-up. */
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

/**
 * B13 — "Ai đã truy cập dữ liệu của tôi" (`GET /account/privacy/access-log`, p15 §15.4).
 *
 * Endpoint ĐÃ TỒN TẠI nhưng hiện trả mảng rỗng cố định (xem {@link AccessLogEntryView}).
 * Vẫn nối thật: khi W3 cắm `AuditLogService.queryBySubject` vào thì màn hình có dữ liệu ngay,
 * không phải sửa UI; và trong lúc chờ, trạng thái rỗng hiển thị là rỗng THẬT.
 */
export function useAccessLog(): UseQueryResult<AccessLogEntryView[]> {
  return useQuery({
    queryKey: PRIVACY_KEYS.accessLog,
    queryFn: async () => (await apiFetch<{ items: AccessLogEntryView[] }>("/account/privacy/access-log")).items,
    staleTime: 60 * 1000,
  });
}

/* ------------------------------------------------------------------ *
 * Step-up re-authentication (p15 REQ-DSAR-04)
 *
 * Xuất/xoá dữ liệu BẮT BUỘC xác minh lại danh tính; `StepUpVerificationPort` fail-closed
 * nên nếu bỏ bước này mọi lời gọi C6/C9/C13 đều trả 403
 * `DSAR_IDENTITY_VERIFICATION_REQUIRED`.
 *
 * Luồng OTP email (phương thức p15 nêu đích danh): A4 `POST /auth/otp/request`
 * purpose `LOGIN_STEPUP` → A5 `POST /auth/otp/verify` lấy `otpTicket` → A12
 * `POST /auth/reauth` method `EMAIL_OTP`, credential = vé đó.
 * Hooks gọi `/auth/*` đặt ở đây chứ không ở `features/auth` vì eslint `boundaries`
 * cấm feature import feature; đây là cùng convention với wrapper `apiFetch` cục bộ.
 * ------------------------------------------------------------------ */

const STEP_UP_OTP_PURPOSE = "LOGIN_STEPUP";

/** A4 — gửi mã OTP tới email của chính tài khoản đang đăng nhập. */
export function useRequestStepUpOtp(): UseMutationResult<OtpRequestResult, Error, string> {
  return useMutation({
    mutationFn: (email: string) =>
      apiFetch<OtpRequestResult>("/auth/otp/request", {
        method: "POST",
        body: JSON.stringify({ email, purpose: STEP_UP_OTP_PURPOSE }),
      }),
  });
}

/** A5 — đổi mã OTP lấy vé dùng một lần. */
export function useVerifyStepUpOtp(): UseMutationResult<OtpVerifyResult, Error, { email: string; code: string }> {
  return useMutation({
    mutationFn: ({ email, code }) =>
      apiFetch<OtpVerifyResult>("/auth/otp/verify", {
        method: "POST",
        body: JSON.stringify({ email, purpose: STEP_UP_OTP_PURPOSE, code }),
      }),
  });
}

/** A12 — đánh dấu phiên đã step-up (cửa sổ 300 giây theo p11 §11.12.4). */
export function useReauth(): UseMutationResult<
  unknown,
  Error,
  { method: StepUpMethod; credential: string; targetAction: string }
> {
  return useMutation({
    mutationFn: (payload) => apiFetch<unknown>("/auth/reauth", { method: "POST", body: JSON.stringify(payload) }),
  });
}
