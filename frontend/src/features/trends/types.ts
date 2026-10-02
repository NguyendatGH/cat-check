/**
 * Types cục bộ cho `features/trends`. KHÔNG có endpoint `/cats/{catId}/trends` (D13) thật —
 * `cat.api.CatController.getCatTrends` là stub 501 ("CHƯA triển khai", chờ `scan::api`/
 * `insight::api`) tại thời điểm A6 làm module này (xem `docs/handovers/A6.md`). Trends tự tính
 * chuỗi thời gian phía client từ `GET /scans` (E2) + `GET /scans/summary` (E3) — 2 endpoint A6
 * ĐÃ triển khai đầy đủ — thay vì chờ D13.
 */

export type TrendRange = "7D" | "30D" | "90D";

export const TREND_RANGE_DAYS: Record<TrendRange, number> = { "7D": 7, "30D": 30, "90D": 90 };

export interface TrendPoint {
  scanId: string;
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
