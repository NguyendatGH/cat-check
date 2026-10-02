/**
 * Types cho `features/export` — khớp `export.api.dto.*` (p8 §8.4.10 nhóm J, p4 G1).
 */

export type ExportRangePreset = "7D" | "30D" | "90D" | "CUSTOM";

/** Số ngày của từng preset — dùng để hiện khoảng ngày thật trên tiêu đề mục (mockup `10`). */
export const EXPORT_PRESET_DAYS: Record<Exclude<ExportRangePreset, "CUSTOM">, number> = {
  "7D": 7,
  "30D": 30,
  "90D": 90,
};

/** Preset được gợi ý mặc định trong mockup `10`. */
export const EXPORT_RECOMMENDED_PRESET: ExportRangePreset = "30D";

/** 4 mục nội dung báo cáo hợp lệ — khớp `ExportRequestService.VALID_SECTIONS` (BE). */
export type ExportSection = "TREND" | "SCAN_LOG" | "NOTES" | "PROFILE";

export const EXPORT_SECTIONS: ExportSection[] = ["TREND", "SCAN_LOG", "NOTES", "PROFILE"];

export type ExportStatus = "QUEUED" | "RUNNING" | "READY" | "FAILED" | "EXPIRED";

/** `POST /exports` (J1) — khớp `ExportRequestRequest`. */
export interface ExportRequestPayload {
  catId: string;
  rangeFrom: string | null;
  rangeTo: string | null;
  rangePreset: ExportRangePreset;
  sections: ExportSection[];
  locale?: string;
  timezone?: string;
}

/** `ExportJobResponse` (J1/J2/J3). */
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

/** Envelope thật của `GET /cats` (D1) — xem `features/scan/types.ts#CatPage` cho lý do trùng lặp
 * (boundaries cấm feature → feature, mỗi feature tự gọi lại D1 khi cần chọn mèo). */
export interface CatPage<T> {
  items: T[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean };
}

export type ExportWizardStep = 1 | 2 | 3;
