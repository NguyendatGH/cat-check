/**
 * Types cục bộ cho `features/history` (p8 §8.5.4 E2/E3). Dữ liệu quét dùng lại
 * `entities/scan-result` (`ScanListItem`/`ScanSummary`) — file này chỉ giữ tham số lọc.
 */

/**
 * 3 chip lọc thực tế theo khả năng của `GET /scans` (E2: `classification[]`, `disputed`) —
 * KHÔNG dùng chip "Ghi chú & Triệu chứng" như mockup `07` vì `hasNote` luôn `false` ở M3
 * (`cat_note` thuộc module `cat`, chưa có cross-module query port — xem
 * `docs/handovers/A6.md`). Client hiển thị ghi chú qua `GET /cats/{catId}/notes` riêng
 * (module `cat`, ngoài phạm vi feature này).
 */
export type HistoryFilterKey = "ALL" | "ABNORMAL" | "DISPUTED";

export const ABNORMAL_CLASSIFICATIONS = ["LOW", "SLIGHTLY_LOW", "SLIGHTLY_HIGH", "HIGH"] as const;

export interface HistoryFilterParams {
  catId?: string;
  filter: HistoryFilterKey;
  cursor?: string | null;
}
