
export interface PageView<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export interface PolicyView {
  policyType: string;
  version: string;
  locale: string;
  title: string;
  contentMd: string | null;
  contentUrl: string | null;
  effectiveFrom: string;
  requiresReconsent: boolean;
  summaryOfChanges: string | null;
  affectedPurposes: string[];
}

export type PolicyCode = "TERMS" | "PRIVACY" | "COOKIE" | "MEDICAL_DISCLAIMER";

export interface PolicyVersionSummaryView {
  version: string;
  locale: string;
  title: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  current: boolean;
  requiresReconsent: boolean;
  summaryOfChanges: string | null;
  affectedPurposes: string[];
}

export interface PolicyVersionListResponse {
  policyCode: string;
  items: PolicyVersionSummaryView[];
}

export interface PurposeView {
  code: string;
  label: string;
  description: string;
  mandatory: boolean;
  sensitive: boolean;
  defaultState: boolean;
  withdrawEffect: string;
  phase: number;
}

export interface DsarRequestView {
  publicRef: string;
  requestType: string;
  channel: string;
  status: string;
  receivedAt: string;
  ackDueAt: string;
  fulfilDueAt: string;
}

export type DsarRequestType =
  | "ACCESS_EXPORT"
  | "RECTIFY"
  | "ERASE"
  | "RESTRICT"
  | "OBJECT"
  | "WITHDRAW_CONSENT"
  | "PROTECTION_MEASURE"
  | "COMPLAINT";

/** Public care tip (GET /care-tips/{slug}) — the source page an assistant citation links to. */
export interface CareTipView {
  slug: string;
  title: string;
  summary: string | null;
  bodyMd: string | null;
  sourceReference: string | null;
  reviewedAt: string | null;
  publishedAt: string | null;
}
