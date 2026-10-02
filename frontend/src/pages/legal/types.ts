/**
 * Types cho các trang pháp lý — khớp NGUYÊN VĂN response của `PolicyController`/
 * `PrivacyController` (đọc từ `backend/.../privacy/api/dto/*.java`, p8 §8.4.3).
 */

export interface PageView<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

/** C16 — bản hiện hành của TERMS|PRIVACY|COOKIE|MEDICAL_DISCLAIMER (công khai). */
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

/** C1 — một mục đích xử lý dữ liệu đã resolve theo locale (công khai). */
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

/** C13/C14 — một yêu cầu DSAR. */
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
