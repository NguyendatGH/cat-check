// features/trends — biểu đồ xu hướng pH theo thời gian (p9 §9.4.3). Mọi import từ bên ngoài
// PHẢI qua file này (boundaries/entry-point). recharts CHỈ được dùng bên trong feature này
// (và features/export) — xem eslint.config.js.
export { RangeSegmentedControl, PhTrendChart, DistributionBar, phBandBoundaries } from "./components";
export type {
  RangeSegmentedControlProps,
  PhTrendChartProps,
  PhSeriesPoint,
  DistributionBarProps,
} from "./components";
export {
  trendsKeys,
  useTrendSeries,
  useTrendSummary,
  useDistributionBuckets,
} from "./hooks";
export type { TrendSeriesResult } from "./hooks";
export { apiFetch as trendsApiFetch, fetchAllScansInRange, fetchScanSummary } from "./api";
export { handlers, worker } from "./mocks";
export { TREND_RANGE_DAYS } from "./types";
export type { TrendRange, TrendPoint, DistributionBucket } from "./types";
