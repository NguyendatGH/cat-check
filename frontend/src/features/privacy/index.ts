// features/privacy — Trung tâm quyền riêng tư: consent (C1–C4), bảng kiểm kê dữ liệu (C5),
// DSAR xuất/xoá/hạn chế (C6–C15) và step-up re-auth (A4/A5/A12).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  DELETION_GRACE_DAYS,
  PRIVACY_KEYS,
  useCancelAccountDeletion,
  useConsentHistory,
  useConsentPurposes,
  useCreateDsarRequest,
  useCreateExportRequest,
  useCurrentConsents,
  useDataInventory,
  useDownloadExport,
  useDsarRequests,
  useExportStatus,
  useReauth,
  useRecordConsents,
  useRequestAccountDeletion,
  useRequestStepUpOtp,
  useSetRestriction,
  useVerifyStepUpOtp,
} from "./hooks";

export type {
  ConsentHistoryView,
  ConsentStateView,
  ConsentStatus,
  DataInventoryView,
  DsarRequestType,
  DsarRequestView,
  DsarStatus,
  ExportStatusView,
  ManualDsarType,
  PurposeView,
  StepUpMethod,
} from "./types";
