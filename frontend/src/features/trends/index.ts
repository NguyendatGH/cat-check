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
