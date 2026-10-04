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

/**
 * F9 — một dòng của `GET /policies/{code}/versions` (p8 §8.4.6, REQ-LEGAL-02).
 * KHÔNG có `contentMd`: danh sách chỉ để chọn bản cần đọc, nội dung lấy qua permalink F10.
 */
export interface PolicyVersionSummaryView {
  version: string;
  locale: string;
  title: string;
  effectiveFrom: string;
  /** `null` = bản đang hiệu lực. */
  effectiveTo: string | null;
  current: boolean;
  requiresReconsent: boolean;
  summaryOfChanges: string | null;
  affectedPurposes: string[];
}

/** F9 — envelope không phân trang (`{policyCode, items}`), mới nhất trước. */
export interface PolicyVersionListResponse {
  policyCode: string;
  items: PolicyVersionSummaryView[];
}

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
