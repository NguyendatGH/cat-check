
export interface PageView<T> {
  items: T[];
  limit: number;
  nextCursor: string | null;
  hasMore: boolean;
}

export type ConsentStatus = "GRANTED" | "DENIED" | "WITHDRAWN" | "NONE";

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

export interface ConsentStateView {
  purposeCode: string;
  status: ConsentStatus;
}

export interface ConsentHistoryView {
  purposeCode: string;
  status: ConsentStatus;
  policyVersion: string | null;
  policyHash: string | null;
  method: string;
  uiSurface: string | null;
  occurredAt: string;
}

export interface DataInventoryView {
  code: string;
  category: string;
  description: string;
  sensitivity: "BASIC" | "SENSITIVE";
  legalBasis: "CONSENT" | "CONTRACT" | "LEGAL_OBLIGATION" | "VITAL_INTEREST";
  purposes: string[];
  retentionPolicyCode?: string;
  storageLocation: string;
  crossBorder: boolean;
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

export type ManualDsarType = "RECTIFY" | "OBJECT" | "PROTECTION_MEASURE" | "COMPLAINT";

export type DsarStatus = "RECEIVED" | "IDENTITY_PENDING" | "IN_PROGRESS" | "EXTENDED" | "COMPLETED" | "REJECTED";

export interface DsarRequestView {
  publicRef: string;
  requestType: DsarRequestType;
  channel: string;
  status: DsarStatus;
  receivedAt: string;
  ackDueAt: string;
  fulfilDueAt: string;
}

export interface ExportStatusView {
  publicRef: string;
  status: DsarStatus;
  ackDueAt: string;
  fulfilDueAt: string;
  resultExpiresAt: string | null;
  completedAt: string | null;
  downloadToken?: string | null;
}

export interface AccessLogEntryView {
  occurredAt: string;
  actorType: string;
  actorRole: string | null;
  action: string;
  result: string;
}

export type StepUpMethod = "EMAIL_OTP" | "PASSWORD";

export interface OtpRequestResult {
  otpExpiresAt: string;
  canResendInSeconds: number;
  maskedEmail: string;
}

export interface OtpVerifyResult {
  otpTicket: string;
  ticketExpiresAt: string;
  purpose: string;
}
