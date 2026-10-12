import type { Cat, CatLastScan } from "./model";

interface ScanLike {
  capturedAt: string;
  phValue: number | null;
  classification: string;
  bandCode: string;
}

export function resolveCatLastScan(cat: Pick<Cat, "lastScanAt" | "lastClassification">, scan?: ScanLike | null): CatLastScan | null {
  if (!cat.lastScanAt) return null;
  if (scan) {
    return {
      capturedAt: scan.capturedAt,
      classification: scan.classification,
      bandCode: scan.bandCode,
      phValue: scan.phValue,
    };
  }
  const code = cat.lastClassification ?? "";
  return { capturedAt: cat.lastScanAt, classification: code, bandCode: code, phValue: null };
}
