

export interface RegisterFormValues {
  fullName: string;
  email: string;
  password: string;
  referralCodeRaw: string;
}

export interface PendingRegistration {
  email: string;
  password: string;
  fullName: string;
  referralCodeRaw: string;
  consents: ConsentGrantDraft[];
}

export interface ConsentGrantDraft {
  purposeCode: string;
  granted: boolean;
}

export interface RegisterResponse {
  userId: string;
  email: string;
  emailVerified: boolean;
  authenticated: boolean;
}

export type OtpPurpose =
  | "REGISTER_VERIFY"
  | "EMAIL_CHANGE"
  | "LOGIN_STEPUP"
  | "DSAR_VERIFY"
  | "PASSWORD_RESET"
  | "ACCOUNT_DELETE_CONFIRM"
  | "DATA_EXPORT_CONFIRM";

export interface OtpRequestResponse {
  otpExpiresAt: string;
  canResendInSeconds: number;
  maskedEmail: string;
}

export interface OtpVerifyResponse {
  otpTicket: string;
  ticketExpiresAt: string;
  purpose: string;
}


export interface ConsentPurposeOption {
  code: string;
  label: string;
  description: string;
  mandatory: boolean;
  sensitive: boolean;
  defaultState: boolean;
  withdrawEffect: string;
  phase: number;
}


export interface LoginFormValues {
  email: string;
  password: string;
  rememberMe: boolean;
}

export interface LoginMfaChallenge {
  mfaRequired: true;
  mfaMethods: string[];
}

export interface LoginSuccess {
  authenticated: true;
  userId: string;
  roles: string[];
}

export type LoginResponse = LoginMfaChallenge | LoginSuccess;

export function isMfaChallenge(value: LoginResponse): value is LoginMfaChallenge {
  return "mfaRequired" in value;
}

export interface TotpLoginVerifyResponse {
  mfaLevel: "TOTP";
}

export interface TotpRecoveryResponse {
  mfaLevel: "TOTP";
  enrollmentRequired: boolean;
  recoveryCodesRemaining: number;
}


export interface ForgotPasswordFormValues {
  email: string;
}

export interface ResetPasswordFormValues {
  newPassword: string;
  confirmPassword: string;
}

export interface SessionBootstrapResponse {
  authenticated: boolean;
  user: unknown;
  roles: string[];
  mfa: unknown;
  serverTime: string;
}


export interface SessionItem {
  id: string;
  deviceLabel: string | null;
  ipMasked: string | null;
  lastSeenAt: string;
  createdAt: string;
  current: boolean;
}

export interface SessionListResponse {
  items: SessionItem[];
}


export interface ProfileResponse {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  locale: string;
  timezone: string | null;
  status: string;
  onboardingStatus: string;
  emailVerified: boolean;
  identities: string[];
  createdAt: string;
}

export interface UpdateProfileRequest {
  fullName?: string;
  phone?: string;
  locale?: string;
  timezone?: string;
}


export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}


export interface EmailChangeRequestResponse {
  otpExpiresAt: string;
  maskedNewEmail: string;
}


export interface IdentityItem {
  provider: string;
  providerEmail: string | null;
  emailVerified: boolean;
  lastUsedAt: string | null;
}

export interface IdentityListResponse {
  items: IdentityItem[];
}


export type TotpStatusValue = "NONE" | "PENDING" | "ACTIVE" | "LOCKED";

export interface TotpStatusResponse {
  status: TotpStatusValue;
  activatedAt: string | null;
  recoveryCodesRemaining: number | null;
  lockedUntil: string | null;
}

export interface TotpEnrollmentResponse {
  secretBase32: string;
  otpauthUri: string;
  expiresAt: string;
  digits: number;
  periodSeconds: number;
  algorithm: string;
}

export interface TotpConfirmResponse {
  recoveryCodes: string[];
  activatedAt: string;
  mfaLevel: "TOTP";
}

export interface TotpRegenerateResponse {
  recoveryCodes: string[];
  activatedAt: string;
}
