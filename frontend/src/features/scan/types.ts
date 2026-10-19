import type { CaptureSource } from "@/entities/scan-result";


export interface SubmitScanMetadata {
  scanRequestId: string;
  catId: string | null;
  assignment: "ASSIGNED" | "SHARED_UNKNOWN";
  capturedAt: string;
  captureSource: CaptureSource;
  imageWidth?: number;
  imageHeight?: number;
}

export interface ReassignResult {
  scanId: string;
  fromCatId: string | null;
  toCatId: string | null;
  reassignRemaining: number;
}

export interface DisputeResult {
  scanId: string;
  disputedAt: string;
  excludedFromTrends: boolean;
}

export interface CatPage<T> {
  items: T[];
  page: { limit: number; nextCursor: string | null; hasMore: boolean };
}
