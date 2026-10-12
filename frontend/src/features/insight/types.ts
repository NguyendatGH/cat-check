
export type HealthFlagSeverity = "INFO" | "ATTENTION" | "URGENT";

export interface HealthFlagView {
  flagId: string;
  catId: string;
  ruleCode: string;
  severity: HealthFlagSeverity;
  triggerScanId: string | null;
  triggeredAt: string;
  windowFrom: string;
  windowTo: string;
  messageKey: string;
  messageParams: Record<string, unknown>;
  explanationVi: string | null;
  acknowledgedAt: string | null;
  acknowledged: boolean;
  disclaimerKey: string;
}

export interface HealthFlagListResponse {
  items: HealthFlagView[];
  hasMore: boolean;
  nextCursor: string | null;
}

export interface HealthFlagFilter {
  catId?: string | undefined;
  acknowledged?: boolean | undefined;
  severity?: HealthFlagSeverity | undefined;
  limit?: number | undefined;
}
