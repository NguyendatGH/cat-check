

export interface AdminOffsetPage<T> {
  items: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

export interface PrivacyPage<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}


export type ChartStatus = "DRAFT" | "ACTIVE" | "ARCHIVED";
export type ProductLine = "MINI" | "STANDARD" | "PLUS";

export const CHART_STATUSES: readonly ChartStatus[] = ["DRAFT", "ACTIVE", "ARCHIVED"];
export const PRODUCT_LINES: readonly ProductLine[] = ["MINI", "STANDARD", "PLUS"];

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

export interface ColorChartDetail extends ColorChartSummary {
  points: ColorChartPoint[];
}

export interface CreateColorChartPayload {
  code: string;
  name: string;
  productLine: ProductLine;
  productionBatch?: string;
}

export interface UpdateColorChartPayload {
  name?: string;
  productLine?: ProductLine;
  productionBatch?: string;
}

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
  authorId: string | null;
  reviewedBy: string | null;
  reviewedAt: string | null;
  publishedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCareTipPayload {
  slug: string;
  locale: string;
  kind: CareTipKind;
  category?: CareTipCategory;
  title: string;
  claimType: ClaimType;
}

export interface UpdateCareTipPayload {
  title?: string;
  summary?: string;
  bodyMd?: string;
  claimType?: ClaimType;
  sourceReference?: string;
  sortWeight?: number;
}


export interface AdminSystemStatus {
  databaseReachable: boolean;
  checkedAt: string;
}


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


export type ActivationCodeStatus = "ISSUED" | "REDEEMED" | "VOID";

/** Trạng thái hiển thị: EXPIRED = mã ISSUED đã quá validUntil (backend suy diễn, không lưu DB). */
export type ActivationCodeEffectiveStatus = ActivationCodeStatus | "EXPIRED";

export interface ActivationCodeAdmin {
  id: string;
  codePrefix: string;
  packageCode: string;
  productionBatch: string;
  issuedAt: string;
  validUntil: string;
  status: ActivationCodeStatus;
  effectiveStatus: ActivationCodeEffectiveStatus;
  expired: boolean;
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
  status?: ActivationCodeEffectiveStatus | "";
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


export const SHOP_PRODUCT_STATUSES = ["DRAFT", "PUBLISHED", "ARCHIVED"] as const;
export type AdminProductStatus = (typeof SHOP_PRODUCT_STATUSES)[number];

export interface AdminProduct {
  id: string;
  sku: string;
  name: string;
  description: string;
  imageUrl: string | null;
  priceVnd: number;
  compareAtPriceVnd: number | null;
  stockQuantity: number;
  status: AdminProductStatus;
  updatedAt: string;
}

export interface AdminProductPayload {
  sku: string;
  name: string;
  description: string;
  imageUrl: string | null;
  priceVnd: number;
  compareAtPriceVnd: number | null;
  stockQuantity: number;
  status: AdminProductStatus;
}

export const ADMIN_ORDER_STATUSES = ["PENDING_PAYMENT", "PAID", "PACKING", "SHIPPING", "DELIVERED", "CANCELLED"] as const;

export interface AdminOrder {
  id: string;
  orderCode: string;
  status: string;
  paymentMethod: string;
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
  subtotalVnd: number;
  discountVnd: number;
  shippingFeeVnd: number;
  totalVnd: number;
  lines: { product: { id: string; sku: string; name: string }; quantity: number; totalVnd: number }[];
  createdAt: string;
  payosOrderCode: number | null;
  paymentExpiresAt: string | null;
  paidAt: string | null;
  paymentReference: string | null;
  cancelReason: string | null;
  refundNeeded: boolean;
  allowedNextStatuses: string[];
}
