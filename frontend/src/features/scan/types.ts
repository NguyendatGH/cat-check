import type { CaptureSource } from "@/entities/scan-result";

/**
 * Types cục bộ cho `features/scan` — không trùng với `entities/scan-result` (model dữ liệu đã
 * lưu). File này chỉ giữ hình dạng REQUEST/response phụ trợ (p8 §8.5.4 nhóm E).
 */

/** Part `metadata` (JSON) của `POST /scans` multipart — khớp `ScanMetadataRequest` (BE). */
export interface SubmitScanMetadata {
  scanRequestId: string;
  catId: string | null;
  assignment: "ASSIGNED" | "SHARED_UNKNOWN";
  capturedAt: string;
  captureSource: CaptureSource;
  imageWidth?: number;
  imageHeight?: number;
}

/** `POST /scans/{id}/reassign-cat` (E9). */
export interface ReassignResult {
  scanId: string;
  fromCatId: string | null;
  toCatId: string | null;
  reassignRemaining: number;
}

/** `POST /scans/{id}/dispute` (E10). */
export interface DisputeResult {
  scanId: string;
  disputedAt: string;
  excludedFromTrends: boolean;
}

/**
 * Envelope thật của `GET /cats` (D1, `cat.api.CatController` + `CatPageResponse`):
 * `{items, page: {limit, nextCursor, hasMore}}`. `features/scan` cần danh sách mèo để chọn
 * TRƯỚC khi quét (`/scan/select-cat`) nhưng KHÔNG được import `features/cat` (eslint boundaries
 * cấm feature → feature) nên tự gọi lại đúng endpoint D1 — xem `api.ts#listActiveCats`.
 */
export interface CatPage<T> {
  items: T[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean };
}
