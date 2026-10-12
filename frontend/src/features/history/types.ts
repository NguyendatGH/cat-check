
export type HistoryFilterKey = "ALL" | "ABNORMAL" | "DISPUTED";

export const ABNORMAL_CLASSIFICATIONS = ["LOW", "SLIGHTLY_LOW", "SLIGHTLY_HIGH", "HIGH"] as const;

export interface HistoryFilterParams {
  catId?: string;
  filter: HistoryFilterKey;
  cursor?: string | null;
}
