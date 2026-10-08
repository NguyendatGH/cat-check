/**
 * Types của Trung tâm quyền riêng tư — chép NGUYÊN VĂN response của `PrivacyController`
 * (nhóm C1–C15 của p8 §8.4.3) và `AuthController` A4/A5/A12, đọc thẳng từ
 * `backend/.../privacy/api/dto/*.java` và `backend/.../identity/api/dto/*.java`.
 *
 * Không thêm field nào backend chưa trả: thiếu dữ liệu thì UI nói "chưa có API", không bịa.
 */

/** Envelope phân trang chung của module privacy (`PageView<T>`). */
export interface PageView<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

/**
 * Trạng thái của một dòng `consent_record` (p4 §4.4.3 nhóm B).
 * `NONE` chỉ tồn tại ở tầng trình bày — "chưa từng được hỏi", KHÔNG bao giờ ghi xuống DB
 * (p15 §15.3.1 C4: im lặng không phải là đồng ý).
 */
export type ConsentStatus = "GRANTED" | "DENIED" | "WITHDRAWN" | "NONE";

/** C1 — một mục đích xử lý dữ liệu đã resolve theo locale (công khai). */
export interface PurposeView {
  code: string;
  label: string;
  description: string;
  /** `true` chỉ cho `SERVICE_CORE` (P1) và `ORDER_FULFILLMENT` (P3) — không rút được. */
  mandatory: boolean;
  /** `true` ⇒ UI **bắt buộc** hiện nhãn "Dữ liệu nhạy cảm" (p4 B3, Đ6.4 NĐ356). */
  sensitive: boolean;
  /** Bất biến I17: mục tuỳ chọn luôn `false` — không bao giờ tick sẵn. */
  defaultState: boolean;
  withdrawEffect: string;
  phase: number;
}

/** C2 — trạng thái hiện hành đọc từ view `consent_current` (p4 B2), KHÔNG tự tính ở client. */
export interface ConsentStateView {
  purposeCode: string;
  status: ConsentStatus;
}

/** C4 — một dòng trong sổ bằng chứng append-only `consent_record`. */
export interface ConsentHistoryView {
  purposeCode: string;
  status: ConsentStatus;
  policyVersion: string | null;
  policyHash: string | null;
  method: string;
  uiSurface: string | null;
  occurredAt: string;
}

/** C5 — một dòng `data_inventory_item` (p15 §15.2.2 / §15.4.3). */
export interface DataInventoryView {
  code: string;
  category: string;
  description: string;
  sensitivity: "BASIC" | "SENSITIVE";
  legalBasis: "CONSENT" | "CONTRACT" | "LEGAL_OBLIGATION" | "VITAL_INTEREST";
  purposes: string[];
  /**
   * VẮNG hẳn khỏi JSON khi nhóm dữ liệu chưa gắn chính sách lưu trữ — server bật
   * `default-property-inclusion: non_null`, nên không bao giờ là `null` thật.
   */
  retentionPolicyCode?: string;
  storageLocation: string;
  crossBorder: boolean;
  /** Vắng khi không chuyển cho bên thứ ba nào (cùng lý do `non_null`). */
  recipient?: string | null;
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

/** Bốn loại `POST /privacy/requests` chấp nhận (C13, `SELF_SERVICE_MANUAL_TYPES`). */
export type ManualDsarType = "RECTIFY" | "OBJECT" | "PROTECTION_MEASURE" | "COMPLAINT";

export type DsarStatus = "RECEIVED" | "IDENTITY_PENDING" | "IN_PROGRESS" | "EXTENDED" | "COMPLETED" | "REJECTED";

/** C13/C14 — một yêu cầu DSAR trong danh sách của tôi. */
export interface DsarRequestView {
  publicRef: string;
  requestType: DsarRequestType;
  channel: string;
  status: DsarStatus;
  receivedAt: string;
  ackDueAt: string;
  fulfilDueAt: string;
}

/** C6/C7 — trạng thái gói xuất dữ liệu. */
export interface ExportStatusView {
  publicRef: string;
  status: DsarStatus;
  ackDueAt: string;
  fulfilDueAt: string;
  /** Hạn 72 giờ của link tải một lần (p15 §15.4.5); `null` khi gói chưa dựng xong. */
  resultExpiresAt: string | null;
  completedAt: string | null;
  /** Chỉ có trong response C6; token bearer 256-bit, giữ ở sessionStorage tới lần tải đầu. */
  downloadToken?: string | null;
}

/**
 * B13 — một dòng "ai đã truy cập dữ liệu của tôi" (`GET /account/privacy/access-log`,
 * p15 §15.4). Tên trường theo đúng cột `audit_log` lọc theo `subject_user_id`
 * (V15__ops.sql K0) — `actor_type`/`actor_role`/`action`/`result`/`occurred_at`.
 *
 * Endpoint trả dữ liệu thật từ audit log. `action` là mã thô — hiển thị qua
 * `accessLogActionKey` (nhãn i18n), không in thẳng ra UI.
 *
 * Audit log không bao giờ chứa PII thô (p4 §4.6.4) nên không có tên/email ở đây, và client
 * cũng không được log lại nội dung dòng nào.
 */
export interface AccessLogEntryView {
  occurredAt: string;
  /** `USER` | `ADMIN` | `DPO` | `SYSTEM` | `JOB`. */
  actorType: string;
  actorRole: string | null;
  action: string;
  /** `SUCCESS` | `DENIED` | `ERROR`. */
  result: string;
}

/** Phương thức step-up re-auth mà `SessionService#reauthenticate` chấp nhận thật. */
export type StepUpMethod = "EMAIL_OTP" | "PASSWORD";

/** A4 — kết quả gửi OTP. */
export interface OtpRequestResult {
  otpExpiresAt: string;
  canResendInSeconds: number;
  maskedEmail: string;
}

/** A5 — vé OTP dùng làm `credential` cho `POST /auth/reauth` method `EMAIL_OTP`. */
export interface OtpVerifyResult {
  otpTicket: string;
  ticketExpiresAt: string;
  purpose: string;
}
