// features/credit — số dư, lịch sử ledger (FEFO), entitlement, kích hoạt mã trong màn
// quản lý tài khoản/settings (p8 §8.4.8 nhóm H1-H4). Mọi import từ bên ngoài PHẢI qua file
// này (boundaries/entry-point).
//
// GHI CHÚ CẤU TRÚC: theo khuôn mẫu bắt buộc của `features/onboarding` (docs/handovers/A7.md
// mục 6) — code nằm ở các file root của feature, không thư mục con.
export {
  ActivationCodeField,
  CreditBalanceSummary,
  CreditBatchCard,
  EntitlementFeatureList,
  LedgerEntryRow,
  formatRemainingSeconds,
} from "./components";
export type { ActivationCodeFieldProps, CreditBalanceSummaryProps, CreditBatchCardProps } from "./components";
export {
  creditKeys,
  entitlementKey,
  packageCatalogKey,
  useActivateCode,
  useCreditBalance,
  useCreditLedger,
  useEntitlement,
  usePackageCatalog,
} from "./hooks";
export { apiFetch } from "./api";
export { handlers, worker } from "./mocks";
export { formatActivationCode, isValidActivationCode, normalizeActivationCode } from "./schemas";
export type {
  ActivationResult,
  CreditBalance,
  CreditBatch,
  CreditLedgerRefType,
  CreditLedgerType,
  Entitlement,
  LedgerEntry,
  LedgerPage,
  PackageCode,
  PackagePlan,
  PackagePlanList,
} from "./types";
