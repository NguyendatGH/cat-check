import type { Cat, CatLastScan } from "./model";

/** Phần của một dòng `GET /scans` mà thẻ mèo cần. */
interface ScanLike {
  capturedAt: string;
  phValue: number | null;
  classification: string;
  bandCode: string;
}

/**
 * Lần quét gần nhất của bé. Sự tồn tại/ngày/dải lấy từ `GET /cats` (`lastScanAt`,
 * `lastClassification`); số pH chỉ có ở `GET /scans` nên được bổ sung khi dòng đó đã tải.
 * `null` = bé chưa có lần quét nào.
 */
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
