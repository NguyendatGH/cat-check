// features/trends — biểu đồ xu hướng pH theo thời gian (p9 §9.4.3). Mọi import từ bên ngoài
// PHẢI qua file này (boundaries/entry-point). recharts CHỈ được dùng bên trong feature này
// (và features/export) — xem eslint.config.js.
//
// Từ W1-E: chỉ còn MỘT nguồn dữ liệu — D13 `GET /cats/{catId}/trends`. Đường tự tổng hợp từ
// `GET /scans` (`useTrendSeries`, `useTrendSummary`, `fetchAllScansInRange`) đã bị xoá vì nó
// cho số liệu lệch với màn Xu hướng desktop.
export { RangeSegmentedControl, PhTrendChart, DistributionBar, phBandBoundaries } from "./components";
export type { RangeSegmentedControlProps, PhTrendChartProps, PhSeriesPoint, DistributionBarProps } from "./components";
export { trendsKeys, useCatTrends, useDistributionBuckets, toTrendPoints } from "./hooks";
export { fetchCatTrends, FEATURE_NOT_IN_PLAN } from "./api";
export { handlers, worker } from "./mocks";
export { TREND_RANGE_DAYS } from "./types";
export type {
  CatTrendBand,
  CatTrendPoint,
  CatTrendStats,
  CatTrendsResponse,
  TrendRange,
  TrendPoint,
  DistributionBucket,
} from "./types";
