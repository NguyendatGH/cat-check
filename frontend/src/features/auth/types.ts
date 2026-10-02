/**
 * Types cho luồng xác thực (p8 §8.4.1 nhóm A, §8.4.2 nhóm B).
 * Tên field khớp NGUYÊN VĂN response thật của `AuthController`/`AccountController`
 * (nhiều DTO record trong backend không được controller dùng — controller trả `Map.of(...)`
 * trực tiếp, nên type ở đây bám theo `Map.of(...)` chứ không bám theo record DTO chưa dùng tới).
 */

/* ---------------- Đăng ký + OTP (A3/A4/A5) ---------------- */

export interface RegisterFormValues {
  fullName: string;
  email: string;
  password: string;
  referralCodeRaw: string;
}

/** Nháp đăng ký giữ tạm giữa /auth/register -> /auth/verify-otp (session-only, không persist). */
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

/** Response thật của `POST /auth/register` (202) — field khớp `RegistrationService.RegistrationResult`. */
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

/** Response thật của `POST /auth/otp/request` (202). */
export interface OtpRequestResponse {
  otpExpiresAt: string;
  canResendInSeconds: number;
  maskedEmail: string;
}

/** Response thật của `POST /auth/otp/verify` (200) — chỉ trả ticket, KHÔNG tạo phiên. */
export interface OtpVerifyResponse {
  otpTicket: string;
  ticketExpiresAt: string;
  purpose: string;
}

/* ---------------- Consent purposes (đọc từ module privacy, C1 công khai) ---------------- */

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

/* ---------------- Đăng nhập + MFA (A6/A10/A11) ---------------- */

export interface LoginFormValues {
  email: string;
  password: string;
  rememberMe: boolean;
}

/** Response thật của `POST /auth/login` — 1 trong 2 hình dạng tuỳ tài khoản có bật TOTP. */
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

/** Response thật của `POST /auth/totp/verify` (buoc 2 dang nhap). */
export interface TotpLoginVerifyResponse {
  mfaLevel: "TOTP";
}

/** Response thật của `POST /auth/totp/recovery`. */
export interface TotpRecoveryResponse {
  mfaLevel: "TOTP";
  enrollmentRequired: boolean;
  recoveryCodesRemaining: number;
}

/* ---------------- Quên / đặt lại mật khẩu (A8/A9) — luồng OTP, KHÔNG phải link email ---------------- */

export interface ForgotPasswordFormValues {
  email: string;
}

export interface ResetPasswordFormValues {
  newPassword: string;
  confirmPassword: string;
}

/**
 * Response thật của `GET /auth/session` (A2, bootstrap). Lưu ý: `user`/`mfa` hiện LUÔN
 * `null` ở backend hôm nay (`AuthController.session()` chưa gán — xem
 * `docs/handovers/A1-fe.md`), chỉ `authenticated`/`roles`/`serverTime` dùng được.
 */
export interface SessionBootstrapResponse {
  authenticated: boolean;
  user: unknown;
  roles: string[];
  mfa: unknown;
  serverTime: string;
}

/* ---------------- Phiên đăng nhập (A13/A14/A15) ---------------- */

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

/* ---------------- Hồ sơ tài khoản (B1/B2) ---------------- */

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

/* ---------------- Đổi mật khẩu (B6) ---------------- */

/**
 * `POST /account/password` — `currentPassword` đóng vai trò step-up PASSWORD, server thu
 * hồi mọi phiên khác khi đổi thành công (AccountController B6). Trả 204, không có body.
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/* ---------------- Đổi email (B7/B8) ---------------- */

export interface EmailChangeRequestResponse {
  otpExpiresAt: string;
  maskedNewEmail: string;
}

/* ---------------- Identity liên kết (B9/B10) ---------------- */

export interface IdentityItem {
  provider: string;
  providerEmail: string | null;
  emailVerified: boolean;
  lastUsedAt: string | null;
}

export interface IdentityListResponse {
  items: IdentityItem[];
}

/* ---------------- MFA TOTP quản lý trong tài khoản (B14-B18) ---------------- */

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
