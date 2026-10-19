

export interface AdminScanItem {
  scanId: string;
  catId?: string | null;
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
  disputedNote?: string | null;
  reassignCount: number;
  imageStored?: boolean | null;
}

export interface AdminScanList {
  items: AdminScanItem[];
  page: {
    number: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasMore: boolean;
  };
  masked: boolean;
}

export type ReassignTarget = { kind: "cat"; catId: string } | { kind: "shared" };

export interface AdminReassignResult {
  scanId: string;
  fromCatId?: string | null;
  toCatId?: string | null;
  toAssignment: string;
  reassignCount: number;
}

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


export type MonitoringRuleSeverity = "INFO" | "ATTENTION" | "URGENT";

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

export interface UpdateMonitoringRulePayload {
  enabled?: boolean;
  params?: Record<string, unknown>;
  cooldownHours?: number;
  pushEnabled?: boolean;
  reason: string;
}


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
  authorityDeadline?: string | null;
  authorityNotificationOverdue?: boolean | null;
  createdAt: string;
}

export interface AdminSecurityIncidentPage {
  items: AdminSecurityIncident[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

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

export interface PurgeAllSessionsResult {
  incidentRef?: string | null;
  revokedCount?: number | null;
  deviceSessionsRevoked?: number | null;
  otpInvalidated?: number | null;
  dsarDownloadLinksRevoked?: number | null;
  elapsedMillis?: number | null;
}


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

export interface AdminPolicyVersionPage {
  items: AdminPolicyVersion[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasMore: boolean;
}

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

export interface PublishPolicyVersionPayload {
  effectiveFrom?: string;
  reason: string;
}


export type ConsentRecordStatus = "GRANTED" | "DENIED" | "WITHDRAWN";

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
