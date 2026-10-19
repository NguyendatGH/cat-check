
export type ExportRangePreset = "7D" | "30D" | "90D" | "CUSTOM";

export const EXPORT_PRESET_DAYS: Record<Exclude<ExportRangePreset, "CUSTOM">, number> = {
  "7D": 7,
  "30D": 30,
  "90D": 90,
};

export const EXPORT_RECOMMENDED_PRESET: ExportRangePreset = "30D";

export type ExportSection = "TREND" | "SCAN_LOG" | "NOTES" | "PROFILE";

export const EXPORT_SECTIONS: ExportSection[] = ["TREND", "SCAN_LOG", "NOTES", "PROFILE"];

export type ExportStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "EXPIRED";

export interface ExportRequestPayload {
  catId: string;
  rangeFrom: string | null;
  rangeTo: string | null;
  rangePreset: ExportRangePreset;
  sections: ExportSection[];
  locale?: string;
  timezone?: string;
}

export interface ExportJob {
  jobId: string;
  catId: string;
  documentCode: string | null;
  status: ExportStatus;
  rangeFrom: string;
  rangeTo: string;
  rangePreset: string | null;
  pageCount: number | null;
  scanCount: number | null;
  downloadCount: number;
  failureReason: string | null;
  requestedAt: string;
  completedAt: string | null;
  expiresAt: string | null;
}

export interface ExportJobPage {
  items: ExportJob[];
  hasMore: boolean;
  nextCursor: string | null;
}

export interface CatPage<T> {
  items: T[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean };
}

export type ExportWizardStep = 1 | 2 | 3;
