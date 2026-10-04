// features/scan — chụp ảnh cát, gửi phân tích pH, xem kết quả, gán lại mèo (p9 §9.4.3, p8
// §8.5.4 nhóm E). Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: toàn bộ code nằm ở các file root của feature (không có thư mục con) —
// cùng lý do `features/onboarding` (xem README/index.ts của feature đó): eslint.config.js
// thiếu policy `feature → feature` nội bộ theo thư mục con nên import sâu bị chặn nhầm.
export {
  CaptureTrigger,
  AnalyzingState,
  QualityFlagList,
  TriggeredFlagList,
  ScanResultSummary,
  ScanAdviceCard,
  InconclusiveNotice,
  SelectCatOption,
  SharedTrayOption,
  MultiCatTip,
} from "./components";
export type {
  CaptureTriggerProps,
  QualityFlagListProps,
  ScanResultSummaryProps,
  InconclusiveNoticeProps,
  SelectCatOptionProps,
  SharedTrayOptionProps,
} from "./components";
export { useScanCaptureStore } from "./store";
export type { CaptureAssignment } from "./store";
export { disputeNoteSchema, DISPUTE_NOTE_MAX } from "./schemas";
export type { DisputeNoteFormValues } from "./schemas";
export {
  scanKeys,
  useScanConfig,
  useActiveCatsForCapture,
  useSubmitScan,
  useScan,
  useScanAnalysis,
  useScanByRequest,
  useReassignScanCat,
  useDisputeScan,
  useClearScanDispute,
  useDeleteScan,
} from "./hooks";
export { apiFetch as scanApiFetch, apiSubmitScan, listActiveCats } from "./api";
export { handlers, worker } from "./mocks";
export type { SubmitScanMetadata, ReassignResult, DisputeResult, CatPage } from "./types";
