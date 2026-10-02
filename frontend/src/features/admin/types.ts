/**
 * Hợp đồng JSON của khu vực quản trị — CHÉP THEO DTO BACKEND ĐANG CHẠY, không tự bịa.
 *
 * Nguồn (đã đọc trực tiếp mã nguồn, không suy đoán):
 * - `colorchart/api/AdminColorChartController.java` + `colorchart/api/dto/*`  → L27–L32, L36, L37
 * - `content/api/AdminContentController.java` + `content/api/dto/*`          → L40–L45
 * - `admin/api/SystemStatusController.java`                                  → `/admin/system-status`
 * - `privacy/api/PrivacyController.java` + `privacy/api/dto/*`               → C1, C5
 *
 * Mã endpoint (L27…) là số hiệu của p8 §8.4.12 — dùng để tra ngược khi backend đổi.
 */

// ---------------------------------------------------------------- phân trang

/** `colorchart/api/dto/OffsetPageResponse` và `content/api/dto/OffsetPageResponse`. */
export interface AdminOffsetPage<T> {
  items: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

/** `privacy/api/dto/PageView` — cursor pagination của nhóm C. */
export interface PrivacyPage<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

// ------------------------------------------------------------- bảng màu pH

export type ChartStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type ProductLine = "MINI" | "STANDARD" | "PLUS";

export const CHART_STATUSES: readonly ChartStatus[] = ["DRAFT", "ACTIVE", "ARCHIVED"];
export const PRODUCT_LINES: readonly ProductLine[] = ["MINI", "STANDARD", "PLUS"];

/** `ColorChartSummaryResponse`. */
export interface ColorChartSummary {
  id: string;
  code: string;
  version: number;
  name: string;
  productLine: ProductLine | null;
  productionBatch: string | null;
  status: ChartStatus;
  placeholder: boolean;
  source: string;
}

/** `ColorChartPointResponse`. `phValue` LUÔN đến từ server — không hằng số ở FE. */
export interface ColorChartPoint {
  id: string;
  phValue: number;
  sortOrder: number;
  labL: number;
  labA: number;
  labB: number;
  toleranceDeltaE: number;
  hexSrgb: string | null;
  displayHex: string;
  displayNameVi: string;
  displayNameEn: string | null;
}

/** `ColorChartDetailResponse`. */
export interface ColorChartDetail extends ColorChartSummary {
  points: ColorChartPoint[];
}

/** `CreateColorChartRequest` — `productLine` là `@NotNull`. */
export interface CreateColorChartPayload {
  code: string;
  name: string;
  productLine: ProductLine;
  productionBatch?: string;
}

/** `UpdateColorChartRequest` (+ `reason`, xem ghi chú ở `api.ts`). */
export interface UpdateColorChartPayload {
  name?: string;
  productLine?: ProductLine;
  productionBatch?: string;
}

/** `PointInput` — server nhận `BigDecimal`, gửi number là hợp lệ. */
export interface ColorChartPointInput {
  phValue: number;
  labL: number;
  labA: number;
  labB: number;
  toleranceDeltaE: number;
  hexSrgb?: string | null;
  displayHex: string;
  displayNameVi: string;
  displayNameEn?: string | null;
}

/** `UpdatePhBandRequest` — mọi trường optional, vắng mặt nghĩa là giữ nguyên. */
export interface UpdatePhBandPayload {
  labelVi?: string;
  labelEn?: string;
  descriptionVi?: string;
  descriptionEn?: string;
  colorToken?: string;
  iconName?: string;
  triggersAlert?: boolean;
  sortOrder?: number;
  active?: boolean;
}

// ------------------------------------------------------------- nội dung CMS

export type CareTipStatus = "DRAFT" | "IN_REVIEW" | "PUBLISHED" | "ARCHIVED";
export type CareTipKind = "TIP" | "ARTICLE" | "FAQ";
export type CareTipCategory = "HYDRATION" | "LITTER" | "SCAN_HOWTO" | "DIET" | "GENERAL";
export type ClaimType =
  | "NONE"
  | "MEDICAL"
  | "STATISTIC"
  | "CERTIFICATION"
  | "PERFORMANCE"
  | "SERVICE_COMMITMENT";

export const CARE_TIP_STATUSES: readonly CareTipStatus[] = ["DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED"];
export const CARE_TIP_KINDS: readonly CareTipKind[] = ["TIP", "ARTICLE", "FAQ"];
export const CARE_TIP_CATEGORIES: readonly CareTipCategory[] = [
  "HYDRATION",
  "LITTER",
  "SCAN_HOWTO",
  "DIET",
  "GENERAL",
];
export const CLAIM_TYPES: readonly ClaimType[] = [
  "NONE",
  "MEDICAL",
  "STATISTIC",
  "CERTIFICATION",
  "PERFORMANCE",
  "SERVICE_COMMITMENT",
];

/** `CareTipDetailResponse`. Chỉ hiển thị trường nào thật sự cần — không phơi `authorId`. */
export interface CareTipDetail {
  id: string;
  slug: string;
  translationGroupId: string;
  locale: string;
  kind: CareTipKind;
  category: CareTipCategory | null;
  title: string;
  summary: string | null;
  bodyMd: string | null;
  coverImageUrl: string | null;
  tags: string[] | null;
  status: CareTipStatus;
  claimType: ClaimType;
  sourceReference: string | null;
  sortWeight: number | null;
  reviewedAt: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

/** `CreateCareTipRequest` — `slug` phải kebab-case, `locale` chỉ `vi`/`en`. */
export interface CreateCareTipPayload {
  slug: string;
  locale: string;
  kind: CareTipKind;
  category?: CareTipCategory;
  title: string;
  claimType: ClaimType;
}

/** `UpdateCareTipRequest` — trường vắng mặt nghĩa là giữ nguyên. */
export interface UpdateCareTipPayload {
  title?: string;
  summary?: string;
  bodyMd?: string;
  claimType?: ClaimType;
  sourceReference?: string;
  sortWeight?: number;
}

// -------------------------------------------------------------- hệ thống

/** `admin/api/dto/SystemStatusResponse`. */
export interface AdminSystemStatus {
  databaseReachable: boolean;
  checkedAt: string;
}

// -------------------------------------------------------------- quyền riêng tư

/** `privacy/api/dto/DataInventoryView` (C5) — danh mục toàn cục, KHÔNG phải dữ liệu của user. */
export interface DataInventoryItem {
  code: string;
  category: string;
  description: string;
  sensitivity: string;
  legalBasis: string;
  purposes: string[] | null;
  retentionPolicyCode: string | null;
  storageLocation: string | null;
  crossBorder: boolean;
  recipient: string | null;
}

/** `privacy/api/dto/PurposeView` (C1) — danh mục `consent_purpose`, công khai. */
export interface ConsentPurposeItem {
  code: string;
  label: string;
  description: string;
  mandatory: boolean;
  sensitive: boolean;
  defaultState: boolean;
  withdrawEffect: string | null;
  phase: number;
}
