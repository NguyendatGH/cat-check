/**
 * Types của `features/trends` — khớp NGUYÊN VĂN `CatTrendsResponse` của backend
 * (`cat/api/dto/CatTrendsResponse.java`, D13 `GET /cats/{catId}/trends`, p8 §8.9).
 *
 * D13 là endpoint THẬT. Bản cũ của file này ghi "`getCatTrends` là stub 501" và vì vậy
 * module tự dựng chuỗi thời gian từ `GET /scans` + `GET /scans/summary`; đường đó đã bị xoá
 * ở W1-E vì nó cho số liệu lệch với màn Xu hướng desktop vốn đã gọi D13.
 */

export type TrendRange = "7D" | "30D" | "90D";

export const TREND_RANGE_DAYS: Record<TrendRange, number> = { "7D": 7, "30D": 30, "90D": 90 };

/** @see CatTrendsResponse.Point — `phValue` là `null` khi lần quét đó `INCONCLUSIVE`. */
export interface CatTrendPoint {
  capturedAt: string;
  phValue: number | null;
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

/** Dải pH kèm trong response D13 — cùng nguồn với `entities/ph-bands`, KHÔNG hard-code ngưỡng. */
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

/** Một điểm đã lọc bỏ lần quét không kết luận được — dùng để vẽ biểu đồ. */
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
