/**
 * Kiểu dữ liệu cho 5 trang quản trị mới. Mỗi kiểu chép đúng DTO backend đã đối chiếu.
 *
 * Backend chạy `default-property-inclusion: non_null` nên trường `null` bị bỏ khỏi JSON:
 * trường có thể rỗng khai `?: T | null`.
 */

// ---------------------------------------------------------------- dữ liệu quét
// scan/api/AdminScanController (L5, L6, L18)

/** `scan/api/dto/AdminScanItemResponse`. Không có trường nào trỏ tới ảnh. */
export interface AdminScanItem {
  scanId: string;
  catId?: string | null;
  /** Đã che khi `masked = true`. */
  catName?: string | null;
  assignment: string;
  capturedAt: string;
  status: string;
  phValue?: number | null;
  classification?: string | null;
  confidence?: number | null;
  nearBoundary: boolean;
  labL?: number | null;
  labA?: number | null;
  labB?: number | null;
  calibrationMethod?: string | null;
  engineVersion?: string | null;
  creditCharged: boolean;
  trial: boolean;
  disputed: boolean;
  /** Đã che khi `masked = true`. */
  disputedNote?: string | null;
  reassignCount: number;
  /** Chỉ có giá trị với DPO đang xử lý DSAR. */
  imageStored?: boolean | null;
}

/** `scan/api/dto/AdminScanListResponse` — phân trang offset. */
export interface AdminScanList {
  items: AdminScanItem[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasMore: boolean;
  };
  /** Response đang che văn bản tự do. */
  masked: boolean;
}

/** Đích chuyển scan: một mèo của chủ scan, hoặc "chưa rõ bé nào". */
export type ReassignTarget = { kind: "cat"; catId: string } | { kind: "shared" };

/** `scan/api/dto/AdminReassignCatResponse`. */
export interface AdminReassignResult {
  scanId: string;
  fromCatId?: string | null;
  toCatId?: string | null;
  toAssignment: string;
  reassignCount: number;
}

/** Phần cần dùng của `cat/api/dto/AdminCatResponse` (GET /admin/users/{userId}/cats). */
export interface AdminUserCat {
  catId: string;
  publicCode?: string | null;
  name?: string | null;
  status: string;
  primary: boolean;
}

export interface AdminUserCatList {
  items: AdminUserCat[];
  masked: boolean;
}

// ------------------------------------------------------------- quy tắc theo dõi
// insight/api/AdminMonitoringRuleController (L38, L39)

export type MonitoringRuleSeverity = "INFO" | "ATTENTION" | "URGENT";

/** `insight/api/dto/AdminMonitoringRuleResponse`. Mỗi dòng mang `etag` riêng cho `If-Match`. */
export interface AdminMonitoringRule {
  code: string;
  name: string;
  enabled: boolean;
  params?: Record<string, unknown> | null;
  severity: MonitoringRuleSeverity;
  cooldownHours: number;
  requiresCalibratedChart: boolean;
  messageKey: string;
  pushEnabled: boolean;
  sortOrder?: number | null;
  updatedAt?: string | null;
  etag: string;
}

/** `insight/api/dto/UpdateMonitoringRuleRequest` — merge-patch, vắng trường = không đổi. */
export interface UpdateMonitoringRulePayload {
  enabled?: boolean;
  /** Thay TOÀN BỘ map, không merge từng khoá. */
  params?: Record<string, unknown>;
  cooldownHours?: number;
  pushEnabled?: boolean;
  reason: string;
}

// ------------------------------------------------------------------- bảo mật
// privacy/api/AdminSecurityIncidentController (L59–L61), AdminSecuritySessionController (L62)

export const INCIDENT_SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"] as const;
export type IncidentSeverity = (typeof INCIDENT_SEVERITIES)[number];

export const INCIDENT_CATEGORIES = [
  "DATA_BREACH",
  "DATA_LOSS",
  "UNAUTHORIZED_ACCESS",
  "AVAILABILITY",
  "OTHER",
] as const;
export type IncidentCategory = (typeof INCIDENT_CATEGORIES)[number];

/** `privacy/api/dto/AdminSecurityIncidentResponse`. */
export interface AdminSecurityIncident {
  id: string;
  publicRef: string;
  severity: IncidentSeverity;
  category: IncidentCategory;
  summary: string;
  affectedSubjectCount?: number | null;
  affectedDataCodes?: string[] | null;
  detectedAt: string;
  classifiedAt?: string | null;
  containedAt?: string | null;
  authorityNotifiedAt?: string | null;
  subjectsNotifiedAt?: string | null;
  resolvedAt?: string | null;
  retainUntil?: string | null;
  handledBy?: string | null;
  reportRef?: string | null;
  /** `detectedAt + 72 giờ`, server tính sẵn. */
  authorityDeadline?: string | null;
  authorityNotificationOverdue?: boolean | null;
  createdAt: string;
}

/** `AdminSecurityIncidentController.PageResponse` — envelope offset phẳng. */
export interface AdminSecurityIncidentPage {
  items: AdminSecurityIncident[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

/** `privacy/api/dto/OpenSecurityIncidentRequest`. */
export interface OpenSecurityIncidentPayload {
  severity: IncidentSeverity;
  category: IncidentCategory;
  summary: string;
  affectedSubjectCount?: number;
  affectedDataCodes?: string[];
  detectedAt?: string;
  reportRef?: string;
  reason: string;
}

/** `privacy/api/dto/UpdateSecurityIncidentRequest` — merge-patch. */
export interface UpdateSecurityIncidentPayload {
  severity?: IncidentSeverity;
  category?: IncidentCategory;
  summary?: string;
  affectedSubjectCount?: number;
  affectedDataCodes?: string[];
  classifiedAt?: string;
  containedAt?: string;
  authorityNotifiedAt?: string;
  subjectsNotifiedAt?: string;
  resolvedAt?: string;
  reportRef?: string;
  reason: string;
}

/** `privacy/api/dto/PurgeAllSessionsResponse`. */
export interface PurgeAllSessionsResult {
  incidentRef?: string | null;
  revokedCount?: number | null;
  deviceSessionsRevoked?: number | null;
  otpInvalidated?: number | null;
  dsarDownloadLinksRevoked?: number | null;
  elapsedMillis?: number | null;
}

// ------------------------------------------------------------ phiên bản chính sách
// privacy/api/AdminPolicyVersionController (L46–L48)

export const POLICY_TYPES = [
  "TERMS",
  "PRIVACY",
  "COOKIE",
  "MEDICAL_DISCLAIMER",
  "COMMUNITY_RULES",
  "RETURNS",
  "PAYMENT",
] as const;
export type PolicyType = (typeof POLICY_TYPES)[number];

export const POLICY_LOCALES = ["vi", "en"] as const;
export type PolicyLocale = (typeof POLICY_LOCALES)[number];

export type PolicyVersionStatus = "DRAFT" | "SCHEDULED" | "EFFECTIVE" | "SUPERSEDED";

/** `privacy/api/dto/AdminPolicyVersionResponse` — không có `contentMd`. */
export interface AdminPolicyVersion {
  id: string;
  policyType: PolicyType;
  version: string;
  locale: string;
  title: string;
  hasContent?: boolean | null;
  contentUrl?: string | null;
  contentHash?: string | null;
  summaryOfChanges?: string | null;
  requiresReconsent?: boolean | null;
  affectedPurposes?: string[] | null;
  effectiveFrom?: string | null;
  effectiveTo?: string | null;
  publishedBy?: string | null;
  createdAt?: string | null;
  status: PolicyVersionStatus;
}

/** `privacy/api/dto/AdminPolicyVersionListResponse`. */
export interface AdminPolicyVersionPage {
  items: AdminPolicyVersion[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

/** `privacy/api/dto/CreatePolicyVersionRequest`. */
export interface CreatePolicyVersionPayload {
  policyType: PolicyType;
  version: string;
  locale: PolicyLocale;
  title: string;
  contentMd?: string;
  contentUrl?: string;
  summaryOfChanges?: string;
  requiresReconsent: boolean;
  affectedPurposes?: string[];
  effectiveFrom: string;
  reason: string;
}

/** `privacy/api/dto/PublishPolicyVersionRequest`. */
export interface PublishPolicyVersionPayload {
  /** `null`/vắng = giữ ngày hiệu lực của bản nháp. */
  effectiveFrom?: string;
  reason: string;
}

// ------------------------------------------------------------------- consent
// privacy/api/AdminPrivacyConsentController (L55)

export type ConsentRecordStatus = "GRANTED" | "DENIED" | "WITHDRAWN";

/** `privacy/api/dto/AdminConsentRecordView`. */
export interface AdminConsentRecord {
  id: string;
  userId: string;
  purposeCode: string;
  status: ConsentRecordStatus;
  policyVersionId?: string | null;
  policyHash?: string | null;
  consentTextHash?: string | null;
  method: string;
  uiSurface?: string | null;
  locale?: string | null;
  supersedesId?: string | null;
  occurredAt: string;
  ipAddress?: string | null;
  userAgent?: string | null;
  requestId?: string | null;
  evidence?: Record<string, unknown> | null;
  createdAt?: string | null;
}
