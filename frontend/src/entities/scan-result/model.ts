/**
 * Model cho kết quả quét — khớp `ScanResultResponse` / `ScanListItemResponse` /
 * `ScanSummaryResponse` / `ScanAnalysisResponse`
 * (`backend/src/main/java/com/catcheck/scan/api/dto/*.java`, p8 §8.5.4).
 *
 * Tên thư mục entity là `scan-result` (khái niệm domain phía FE — "kết quả 1 lần scan"),
 * KHÔNG PHẢI tên bảng: bảng thật là `scan` + `scan_analysis` (p4 D1/D3, KHÔNG có bảng
 * `scan_result`).
 *
 * Nhãn hiển thị (label/màu/icon) của `classification` KHÔNG đặt ở đây — tra qua
 * `entities/ph-bands` (`usePhBands()`/`findBandForPh()`), theo đúng "một nguồn sự thật".
 * File này chỉ giữ hình dạng dữ liệu thô từ API.
 */

export type ScanClassification = "IN_RANGE" | "SLIGHTLY_LOW" | "SLIGHTLY_HIGH" | "LOW" | "HIGH" | "INCONCLUSIVE";

export type ConfidenceBand = "HIGH" | "MEDIUM" | "LOW";

/** `UNASSIGNED` là trạng thái nội bộ BE, API không bao giờ trả về giá trị này (p8 §8.5.4). */
export type ScanAssignment = "ASSIGNED" | "SHARED_UNKNOWN";

export type CaptureSource = "CAMERA" | "GALLERY";

export type CalibrationMethod = "CARD_CCM" | "SUBSTRATE_WB" | "NONE";

export type QualityFlagSeverity = "WARN" | "BLOCKING";

export interface QualityFlag {
  code: string;
  severity: QualityFlagSeverity;
  messageKey: string;
}

export interface TriggeredFlag {
  flagId: string;
  ruleCode: string;
  severity: string;
  messageKey: string;
}

/** `POST /scans` và `GET /scans/{id}` (p8 §8.5.4) — `scanId` là `null` khi và chỉ khi INCONCLUSIVE. */
export interface ScanResult {
  scanId: string | null;
  scanRequestId: string;
  catId: string | null;
  catName: string | null;
  assignment: ScanAssignment;
  capturedAt: string;
  status: string;

  phValue: number | null;
  phLow: number | null;
  phHigh: number | null;
  classification: ScanClassification;
  bandCode: string;
  labelKey: string;
  nearBoundary: boolean;
  confidence: number | null;
  confidenceBand: ConfidenceBand | null;

  matchPercent: number | null;
  displayHex: string | null;
  calibrationMethod: CalibrationMethod | null;
  captureSource: CaptureSource | null;
  chartCode: string | null;
  chartVersion: number | null;
  chartIsPlaceholder: boolean;
  engineVersion: string | null;

  qualityFlags: QualityFlag[];

  creditCharged: boolean;
  creditBalanceAfter: number | null;
  isTrial: boolean;
  imageStored: boolean;
  imageUrl: string | null;
  imageRetainedUntil: string | null;
  storeImageReason: string | null;
  reassignableUntil: string | null;
  reassignRemaining: number;

  triggeredFlags: TriggeredFlag[];
  disclaimerKey: string;
  emergencyDisclaimerKey: string;
  processingMs: number | null;

  retryHintKey: string | null;
  disputedAt: string | null;
  disputedNote: string | null;
}

/** Một dòng `GET /scans` (p8 §8.5.4 E2). */
export interface ScanListItem {
  scanId: string;
  catId: string | null;
  catName: string | null;
  capturedAt: string;
  phValue: number | null;
  classification: ScanClassification;
  bandCode: string;
  confidence: number | null;
  confidenceBand: ConfidenceBand | null;
  nearBoundary: boolean;
  thumbnailHex: string | null;
  imageAvailable: boolean;
  disputed: boolean;
  hasNote: boolean;
}

export interface ScanListPage {
  items: ScanListItem[];
  hasMore: boolean;
  nextCursor: string | null;
}

/** `GET /scans/summary` (p8 §8.5.4 E3). */
export interface ScanSummary {
  count: number;
  byClassification: Record<string, number>;
  median: number | null;
  min: number | null;
  max: number | null;
  inconclusiveCount: number;
  lowConfidenceCount: number;
  firstAt: string | null;
  lastAt: string | null;
}

/** `GET /scans/{id}/analysis` (p8 §8.5.4 E5). */
export interface ScanAnalysisDetail {
  labL: number | null;
  labA: number | null;
  labB: number | null;
  labSpreadDe00: number | null;
  blobCount: number | null;
  indicatorPixelRatio: number | null;
  substrateLabL: number | null;
  substrateLabA: number | null;
  substrateLabB: number | null;
  deltaEMin: number | null;
  perpResidualDe00: number | null;
  calibrationMethod: CalibrationMethod | null;
  calibrationResidualDe00: number | null;
  qualityMetrics: Record<string, number> | null;
  chartCode: string | null;
  chartVersion: number | null;
  engineVersion: string | null;
  computedAt: string;
  processingMs: number | null;
  recomputeOf: string | null;
}

/** `GET /scan/config` (p8 §8.5.4 E12). */
export interface ScanConfig {
  maxEdgePx: number;
  minEdgePx: number;
  jpegQuality: number;
  cropMarginPct: number;
  maxBytes: number;
  acceptedTypes: string[];
  requireCalibratedChart: boolean;
  precheckThresholds: {
    blurVarMin: number;
    meanLumaMin: number;
    meanLumaMax: number;
    clipHighMax: number;
    tiltDegMax: number;
  };
  minResultConfidence: number;
  activeChart: { code: string | null; version: number; isPlaceholder: boolean };
}

/**
 * Số lượt quét **hiển thị được** của một `ScanSummary`.
 *
 * Vì sao cần hàm này: `GET /scans/summary` (E3) đếm MỌI bản ghi `scan`, kể cả
 * `INCONCLUSIVE` — bản ghi giữ lại để audit nhưng `GET /scans` (E2) loại hẳn ra
 * (`JdbcScanQueryRepository.findHistory`: `sa.classification <> 'INCONCLUSIVE'`), và
 * `GET /scans/{id}` cũng trả 404 cho chúng. Hệ quả: màn nào lấy `summary.count` làm "tổng
 * bản ghi" sẽ luôn lớn hơn số dòng người dùng thật sự nhìn thấy và bấm được.
 *
 * Mọi chỗ nói "tổng lượt quét" PHẢI dùng hàm này để một bộ dữ liệu chỉ có một con số.
 */
export function displayableScanCount(summary: Pick<ScanSummary, "count" | "inconclusiveCount">): number {
  return Math.max(0, summary.count - summary.inconclusiveCount);
}
