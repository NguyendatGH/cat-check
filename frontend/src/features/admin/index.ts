// features/admin — khu vực quản trị mức MVP (quyết định owner #14).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).

export { ADMIN_REASON_MIN_LENGTH, isReasonValid, isHexColor } from "./api";

export {
  adminKeys,
  useAdminSystemStatus,
  useAdminColorCharts,
  useAdminColorChart,
  useCreateColorChart,
  useUpdateColorChart,
  useReplaceChartPoints,
  usePublishColorChart,
  useAdminPhBands,
  useUpdatePhBand,
  useAdminCareTips,
  useCreateCareTip,
  useUpdateCareTip,
  useSubmitCareTipReview,
  usePublishCareTip,
  useArchiveCareTip,
  useAdminDataInventory,
  useAdminConsentPurposes,
} from "./hooks";

export {
  AdminPageHeader,
  AdminSection,
  AdminTableScroll,
  AdminSelect,
  ReasonField,
  ApiErrorNote,
  SavedNote,
  Pager,
  MissingApiNotice,
  adminTableClass,
  adminThClass,
  adminTdClass,
} from "./components";
export type { MissingApiEndpoint, MissingApiNoticeProps } from "./components";

export {
  CHART_STATUSES,
  PRODUCT_LINES,
  CARE_TIP_STATUSES,
  CARE_TIP_KINDS,
  CARE_TIP_CATEGORIES,
  CLAIM_TYPES,
} from "./types";

export type {
  AdminOffsetPage,
  PrivacyPage,
  ChartStatus,
  ProductLine,
  ColorChartSummary,
  ColorChartPoint,
  ColorChartDetail,
  CreateColorChartPayload,
  UpdateColorChartPayload,
  ColorChartPointInput,
  UpdatePhBandPayload,
  CareTipStatus,
  CareTipKind,
  CareTipCategory,
  ClaimType,
  CareTipDetail,
  CreateCareTipPayload,
  UpdateCareTipPayload,
  AdminSystemStatus,
  DataInventoryItem,
  ConsentPurposeItem,
} from "./types";
