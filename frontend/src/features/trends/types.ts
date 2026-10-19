
export type TrendRange = "7D" | "30D" | "90D";

export const TREND_RANGE_DAYS: Record<TrendRange, number> = { "7D": 7, "30D": 30, "90D": 90 };

export interface CatTrendPoint {
  capturedAt: string;
  phValue?: number | null;
  classification: string;
  confidence: number | null;
  nearBoundary: boolean;
  disputed: boolean;
}

export interface CatTrendStats {
  median: number | null;
  min: number | null;
  max: number | null;
  count: number;
  inRangeCount: number;
  lowConfidenceCount: number;
}

export interface CatTrendBand {
  code: string;
  minPh: number | null;
  maxPh: number | null;
  severity: string;
  label: string;
  colorToken: string;
  sortOrder: number;
}

export interface CatTrendsResponse {
  range: string;
  from: string;
  to: string;
  points: CatTrendPoint[];
  stats: CatTrendStats;
  bands: CatTrendBand[];
  chartVersions: number[];
}

export interface TrendPoint {
  date: string;
  phValue: number;
  classification: string;
  isLatest: boolean;
}

export interface DistributionBucket {
  classification: string;
  count: number;
  percent: number;
}
