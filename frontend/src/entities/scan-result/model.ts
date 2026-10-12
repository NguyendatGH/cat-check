
export type ScanClassification = "IN_RANGE" | "SLIGHTLY_LOW" | "SLIGHTLY_HIGH" | "LOW" | "HIGH" | "INCONCLUSIVE";

export type ConfidenceBand = "HIGH" | "MEDIUM" | "LOW";

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

export function displayableScanCount(summary: Pick<ScanSummary, "count" | "inconclusiveCount">): number {
  return Math.max(0, summary.count - summary.inconclusiveCount);
}
