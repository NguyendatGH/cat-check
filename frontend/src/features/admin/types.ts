/**
 * Hợp đồng JSON của khu vực quản trị — CHÉP THEO DTO BACKEND ĐANG CHẠY, không tự bịa.
 *
 * Nguồn (đã đọc trực tiếp mã nguồn, không suy đoán):
 * - `colorchart/api/AdminColorChartController.java` + `colorchart/api/dto/*`  → L27–L32, L36, L37
 * - `content/api/AdminContentController.java` + `content/api/dto/*`          → L40–L45
 * - `admin/api/SystemStatusController.java`                                  → `/admin/system-status`
 * - `privacy/api/PrivacyController.java` + `privacy/api/dto/*`               → C1, C5
 * - `community/api/CommunityModerationController.java` + `community/api/dto/*` → F18
 *
 * Mã endpoint (L27…) là số hiệu của p8 §8.4.12 — dùng để tra ngược khi backend đổi.
 */

// ---------------------------------------------------------------- phân trang

/** `colorchart/api/dto/OffsetPageResponse`, `content/...` và `community/...`. */
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
export type ClaimType = "NONE" | "MEDICAL" | "STATISTIC" | "CERTIFICATION" | "PERFORMANCE" | "SERVICE_COMMITMENT";

export const CARE_TIP_STATUSES: readonly CareTipStatus[] = ["DRAFT", "IN_REVIEW", "PUBLISHED", "ARCHIVED"];
export const CARE_TIP_KINDS: readonly CareTipKind[] = ["TIP", "ARTICLE", "FAQ"];
export const CARE_TIP_CATEGORIES: readonly CareTipCategory[] = ["HYDRATION", "LITTER", "SCAN_HOWTO", "DIET", "GENERAL"];
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

export type RetentionAction = "HARD_DELETE" | "ANONYMIZE" | "ARCHIVE" | "MASK";

export interface AdminRetentionPolicy {
  code: string;
  dataInventoryCode: string | null;
  targetTable: string;
  retentionDays: number | null;
  anchorColumn: string;
  actionOnExpiry: RetentionAction;
  jobName: string | null;
  safetyThresholdPercent: number;
  enabled: boolean;
  legalBasis: string | null;
  updatedBy: string | null;
  createdAt: string;
}

export interface AdminRetentionDryRun {
  policyCode: string;
  targetTable: string;
  anchorColumn: string;
  retentionDays: number | null;
  cutoff: string;
  candidateCount: number;
  actionOnExpiry: string;
}

export type CommunityReportAction = "HIDE_POST" | "REMOVE_POST" | "HIDE_COMMENT" | "DISMISS";

export interface CommunityReport {
  id: string;
  targetType: "POST" | "COMMENT";
  targetId: string;
  reporterName: string;
  reason: string;
  details: string | null;
  status: string;
  targetTitle: string | null;
  createdAt: string;
}

// ---------------------------------------------------------- mã kích hoạt

export type ActivationCodeStatus = "ISSUED" | "REDEEMED" | "VOID";

export interface ActivationCodeAdmin {
  id: string;
  codePrefix: string;
  packageCode: string;
  productionBatch: string;
  issuedAt: string;
  validUntil: string;
  status: ActivationCodeStatus;
  redeemedBy: string | null;
  redeemedAt: string | null;
}

export interface ActivationBatch {
  batchId: string;
  productionBatch: string;
  packageCode: string;
  totalCodes: number;
  issuedCodes: number;
  redeemedCodes: number;
  voidedCodes: number;
  issuedAt: string;
  validUntil: string;
  csvAvailable: boolean;
}

export interface ActivationCodeFilter {
  prefix?: string;
  packageCode?: string;
  status?: ActivationCodeStatus | "";
  batchId?: string;
  page: number;
  size?: number;
}

export interface IssueActivationBatchPayload {
  packageCode: string;
  quantity: number;
  productionBatch: string;
  validForDays: number;
  reason: string;
}

export interface IssuedActivationBatch {
  batchId: string;
  productionBatch: string;
  packageCode: string;
  quantity: number;
  issuedAt: string;
  validUntil: string;
  csvAvailable: boolean;
  csvPath: string;
}

// ---------------------------------------------------------- cấu hình gói

export interface AdminPlanFeatures {
  history: "NONE" | "BASIC" | "ADVANCED";
  trend: boolean;
  reminder: boolean;
  export: boolean;
  storeImage: boolean;
}

export interface AdminPackagePlan {
  code: string;
  name: string;
  weightKg: number;
  creditAmount: number;
  creditValidityDays: number;
  maxCatProfiles: number | null;
  features: AdminPlanFeatures;
  active: boolean;
  version: number;
  updatedAt: string;
  etag: string;
}

export interface UpdateAdminPackagePlanPayload {
  creditAmount?: number;
  creditValidityDays?: number;
  maxCatProfiles?: number;
  clearMaxCatProfiles?: boolean;
  features?: AdminPlanFeatures;
  active?: boolean;
  reason: string;
}

// ---------------------------------------------------------- vận hành

export interface AdminJobRun {
  id: string;
  jobName: string;
  status: string;
  startedAt: string;
  finishedAt: string | null;
  durationMs: number | null;
  rowCount: number | null;
  errorSummary: string | null;
}

export interface AdminOutboxEntry {
  id: string;
  channel: "EMAIL" | "PUSH";
  status: "PENDING" | "SENT" | "FAILED";
  reference: string;
  recipientMasked: string;
  attempts: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  sentAt: string | null;
  createdAt: string;
}

export interface AdminAuditLog {
  id: string;
  occurredAt: string;
  actorType: string;
  actorRole: string | null;
  subjectType: string;
  action: string;
  result: string;
  requestId: string | null;
  metadata: string;
}

export interface AdminMetrics {
  users: number;
  activeCats: number;
  scans: number;
  failedJobs: number;
  pendingOutbox: number;
}

export interface AdminDsarRequest {
  id: string;
  publicRef: string;
  userId: string | null;
  contactEmailMasked: string;
  requestType: string;
  channel: string;
  status: string;
  receivedAt: string;
  ackDueAt: string;
  ackSentAt: string | null;
  fulfilDueAt: string;
  extendedTo: string | null;
  extensionReason: string | null;
  thirdPartyInvolved: boolean;
  completedAt: string | null;
  rejectionReason: string | null;
  handledBy: string | null;
  createdAt: string;
}

export type AdminUserStatus =
  "PENDING_VERIFICATION" | "ACTIVE" | "LOCKED" | "RESTRICTED" | "DELETION_REQUESTED" | "ANONYMIZED";

export interface AdminUserSummary {
  userId: string;
  emailMasked: string;
  fullName: string;
  status: AdminUserStatus;
  onboardingStatus: string;
  emailVerified: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  lockedUntil: string | null;
  catCount: number;
  scanCount: number;
  highestPackage: string | null;
}

export interface AdminUserDetail extends AdminUserSummary {
  phoneMasked: string | null;
  locale: string | null;
  timezone: string | null;
  emailVerifiedAt: string | null;
  deletionScheduledAt: string | null;
  processingRestricted: boolean;
  totpEnabled: boolean;
  activeSessionCount: number;
  roles: string[];
}

export interface AdminUnmaskResponse {
  userId: string;
  email: string;
  phone: string | null;
  unmaskedUntil: string;
}

export interface AdminSessionItem {
  id: string;
  deviceLabel: string | null;
  ipAddressMasked: string | null;
  rememberMe: boolean;
  createdAt: string;
  lastSeenAt: string;
  expiresAt: string;
}

export interface AdminSessionListResponse {
  items: AdminSessionItem[];
  revokedSessions: number;
}

export interface AdminMfaResetRequest {
  id: string;
  targetUserId: string;
  requestedBy: string;
  requestedAt: string;
  reason: string;
  status: string;
  expiresAt: string;
  approvedBy: string | null;
  approvedAt: string | null;
}

export interface AdminMaintenance {
  active: boolean;
  until: string | null;
  updatedAt: string;
}
