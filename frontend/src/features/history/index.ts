// features/history — timeline lịch sử quét theo mèo (p9 §9.4.3, p8 §8.5.4 E2/E3). Mọi import
// từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  CatSwitcherBar,
  ExportCtaCard,
  FilterChips,
  HistoryTipCard,
  MonthGroupHeader,
  PhBandLegendRow,
  ScanTimelineItem,
  formatScanTimestamp,
  monthGroupKey,
  parseMonthGroupKey,
} from "./components";
export type {
  CatSwitcherBarProps,
  ExportCtaCardProps,
  FilterChipsProps,
  HistoryTipCardProps,
  PhBandLegendRowProps,
  ScanTimelineItemProps,
} from "./components";
export { historyKeys, useScanHistory, useScanSummary, useCat } from "./hooks";
export { apiFetch as historyApiFetch, fetchScanHistory, fetchScanSummary, fetchCat } from "./api";
export { handlers, worker } from "./mocks";
export type { HistoryFilterKey, HistoryFilterParams } from "./types";
export { ABNORMAL_CLASSIFICATIONS } from "./types";
