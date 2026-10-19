import type { PhBand } from "@/entities/ph-bands";
import {
  isHexColor,
  type ColorChartPoint,
  type ColorChartPointInput,
  type ColorChartSummary,
} from "@/features/admin";
import { formatNumber } from "@/shared/lib/format/formatNumber";

/**
 * Máy quét hiện luôn hỏi bảng ACTIVE của dòng STANDARD, không gắn lô
 * (backend: ScanThresholds.DEFAULT_PRODUCT_LINE + findActiveChart(line, null)).
 */
export const SCAN_PRODUCT_LINE = "STANDARD";

/** Ngưỡng máy chủ kiểm khi xuất bản (backend ChartPublishValidator). */
export const PUBLISH_MIN_POINTS = 4;
export const REQUIRED_PH_VALUES = [6.3, 6.6] as const;

function normBatch(batch: string | null | undefined): string {
  return batch?.trim() ?? "";
}

export function hasBatch(chart: Pick<ColorChartSummary, "productionBatch">): boolean {
  return normBatch(chart.productionBatch) !== "";
}

export function isUsedByScans(chart: Pick<ColorChartSummary, "status" | "productLine" | "productionBatch">): boolean {
  return chart.status === "ACTIVE" && chart.productLine === SCAN_PRODUCT_LINE && !hasBatch(chart);
}

/** Bảng ACTIVE khác đang chiếm cùng dòng sản phẩm + lô với `chart`. */
export function findActiveConflict(
  chart: Pick<ColorChartSummary, "id" | "productLine" | "productionBatch">,
  candidates: readonly ColorChartSummary[],
): ColorChartSummary | undefined {
  return candidates.find(
    (other) =>
      other.id !== chart.id &&
      other.status === "ACTIVE" &&
      other.productLine === chart.productLine &&
      normBatch(other.productionBatch) === normBatch(chart.productionBatch),
  );
}

export function sortedPoints<T extends Pick<ColorChartPoint, "sortOrder">>(points: readonly T[]): T[] {
  return [...points].sort((a, b) => a.sortOrder - b.sortOrder);
}

export function formatPhShort(value: number): string {
  return formatNumber(value, { minimumFractionDigits: 1, maximumFractionDigits: 2 });
}

export type PublishCheckKey = "minPoints" | "has63" | "has66" | "ascending";

export interface PublishCheck {
  key: PublishCheckKey;
  ok: boolean;
}

/** Xem trước các luật xuất bản của máy chủ trên các mức ĐÃ LƯU. ΔE giữa hai mức kề nhau chỉ máy chủ kiểm. */
export function publishChecks(points: readonly Pick<ColorChartPoint, "phValue" | "sortOrder">[]): PublishCheck[] {
  const ordered = sortedPoints(points);
  const values = ordered.map((point) => point.phValue);
  const ascending = values.every((value, index) => index === 0 || value > (values[index - 1] ?? -Infinity));
  return [
    { key: "minPoints", ok: values.length >= PUBLISH_MIN_POINTS },
    { key: "has63", ok: values.includes(REQUIRED_PH_VALUES[0]) },
    { key: "has66", ok: values.includes(REQUIRED_PH_VALUES[1]) },
    { key: "ascending", ok: values.length > 0 && ascending },
  ];
}

export interface PointDraft {
  key: string;
  phValue: string;
  labL: string;
  labA: string;
  labB: string;
  toleranceDeltaE: string;
  hexSrgb: string;
  displayHex: string;
  displayNameVi: string;
  displayNameEn: string;
  /** Lab đang là giá trị tự tính từ hex (chưa ai nhập tay), nên được tính lại khi hex đổi. */
  labAuto: boolean;
}

export type DraftTextField = Exclude<keyof PointDraft, "key" | "labAuto">;

export const DRAFT_NUMBER_FIELDS = ["phValue", "labL", "labA", "labB", "toleranceDeltaE"] as const;
export const TECH_NUMBER_FIELDS = ["labL", "labA", "labB", "toleranceDeltaE"] as const;

export function toDraft(point: ColorChartPoint): PointDraft {
  return {
    key: point.id,
    phValue: String(point.phValue),
    labL: String(point.labL),
    labA: String(point.labA),
    labB: String(point.labB),
    toleranceDeltaE: String(point.toleranceDeltaE),
    hexSrgb: point.hexSrgb ?? "",
    displayHex: point.displayHex,
    displayNameVi: point.displayNameVi,
    displayNameEn: point.displayNameEn ?? "",
    labAuto: false,
  };
}

export function emptyDraft(): PointDraft {
  return {
    key: `new-${String(Date.now())}-${String(Math.random()).slice(2, 8)}`,
    phValue: "",
    labL: "",
    labA: "",
    labB: "",
    toleranceDeltaE: "",
    hexSrgb: "",
    displayHex: "",
    displayNameVi: "",
    displayNameEn: "",
    labAuto: true,
  };
}

export function draftToInput(draft: PointDraft): ColorChartPointInput {
  return {
    phValue: Number(draft.phValue),
    labL: Number(draft.labL),
    labA: Number(draft.labA),
    labB: Number(draft.labB),
    toleranceDeltaE: Number(draft.toleranceDeltaE),
    hexSrgb: draft.hexSrgb.trim().length > 0 ? draft.hexSrgb.trim() : null,
    displayHex: draft.displayHex.trim(),
    displayNameVi: draft.displayNameVi.trim(),
    displayNameEn: draft.displayNameEn.trim().length > 0 ? draft.displayNameEn.trim() : null,
  };
}

function isNumberText(value: string): boolean {
  return value.trim().length > 0 && !Number.isNaN(Number(value));
}

export function isTechComplete(draft: PointDraft): boolean {
  return (
    TECH_NUMBER_FIELDS.every((field) => isNumberText(draft[field])) &&
    (draft.hexSrgb.trim().length === 0 || isHexColor(draft.hexSrgb.trim()))
  );
}

export function isDraftComplete(draft: PointDraft): boolean {
  return (
    isNumberText(draft.phValue) &&
    isHexColor(draft.displayHex.trim()) &&
    draft.displayNameVi.trim().length > 0 &&
    isTechComplete(draft)
  );
}

export type BandRangeKind = "none" | "below" | "between" | "above";

export interface BandRange {
  kind: BandRangeKind;
  min: number | null;
  max: number | null;
  minInclusive: boolean;
  maxInclusive: boolean;
}

export function bandRange(band: Pick<PhBand, "phMin" | "phMax" | "minInclusive" | "maxInclusive">): BandRange {
  const min = band.phMin ?? null;
  const max = band.phMax ?? null;
  const kind: BandRangeKind =
    min === null && max === null ? "none" : min === null ? "below" : max === null ? "above" : "between";
  return { kind, min, max, minInclusive: band.minInclusive, maxInclusive: band.maxInclusive };
}

/** Dải có khoảng pH, theo thứ tự hiển thị — dùng vẽ thang pH. */
export function scaleBands<T extends Pick<PhBand, "phMin" | "phMax" | "sortOrder">>(bands: readonly T[]): T[] {
  return bands
    .filter((band) => band.phMin != null || band.phMax != null)
    .sort((a, b) => (a.phMin ?? -Infinity) - (b.phMin ?? -Infinity) || a.sortOrder - b.sortOrder);
}

// Cùng công thức với backend ColorSpace.hexToLab (sRGB → XYZ D65 → CIELAB, điểm trắng = tổng hàng
// ma trận) để Lab tự tính khớp seed V9.
const SRGB_TO_XYZ = [
  [0.4123907992659595, 0.35758433938387796, 0.1804807884018343],
  [0.21263900587151036, 0.7151686787677559, 0.07219231536073371],
  [0.01933081871559185, 0.11919477979462599, 0.9505321522496606],
] as const;
const LAB_DELTA = 6 / 29;

function srgbToLinear(channel: number): number {
  return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
}

function labF(t: number): number {
  return t > LAB_DELTA ** 3 ? Math.cbrt(t) : t / (3 * LAB_DELTA * LAB_DELTA) + 4 / 29;
}

/** `#RRGGBB` → CIELAB (D65, 2°). Gọi với hex đã qua isHexColor. */
export function hexToLab(hex: string): { l: number; a: number; b: number } {
  const rgb = [1, 3, 5].map((start) => srgbToLinear(parseInt(hex.slice(start, start + 2), 16) / 255));
  const [fx, fy, fz] = SRGB_TO_XYZ.map((row) => {
    const value = row.reduce((sum, weight, index) => sum + weight * (rgb[index] ?? 0), 0);
    return labF(value / (row[0] + row[1] + row[2]));
  }) as [number, number, number];
  return { l: 116 * fy - 16, a: 500 * (fx - fy), b: 200 * (fy - fz) };
}

function round3(value: number): string {
  return String(Math.round(value * 1000) / 1000);
}

/**
 * Đổi một ô của điểm màu. Khi hex đổi, Lab tự tính lại từ Hex sRGB (nếu hợp lệ) hoặc Hex hiển thị,
 * nhưng chỉ khi Lab đang trống hoặc vẫn là giá trị tự tính: không ghi đè Lab đã nhập/đo tay.
 */
export function patchDraft(draft: PointDraft, field: DraftTextField, value: string): PointDraft {
  const next: PointDraft = { ...draft, [field]: value };
  if (field === "labL" || field === "labA" || field === "labB") return { ...next, labAuto: false };
  if (field !== "displayHex" && field !== "hexSrgb") return next;
  const labEmpty = [draft.labL, draft.labA, draft.labB].every((part) => part.trim().length === 0);
  if (!draft.labAuto && !labEmpty) return next;
  const srgb = next.hexSrgb.trim();
  const hex = isHexColor(srgb) ? srgb : next.displayHex.trim();
  if (!isHexColor(hex)) return next;
  const lab = hexToLab(hex);
  return { ...next, labL: round3(lab.l), labA: round3(lab.a), labB: round3(lab.b), labAuto: true };
}
