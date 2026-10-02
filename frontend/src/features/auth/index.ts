// features/auth — đăng ký/OTP/đăng nhập/MFA/quên-đặt lại mật khẩu/phiên/tài khoản (p8 §8.4.1-8.4.2).
// Mọi import từ bên ngoài PHẢI qua file này (boundaries/entry-point).
export {
  OtpCodeInput,
  PasswordField,
  PasswordStrengthMeter,
  ResendCountdown,
  ConsentCheckboxList,
  GoogleAuthButton,
  SessionRow,
  RecoveryCodeGrid,
  InlineAlert,
} from "./components";

export { useAuthFlowStore } from "./store";

export {
  registerSchema,
  loginSchema,
  otpCodeSchema,
  forgotPasswordSchema,
  resetPasswordSchema,
  totpCodeSchema,
  recoveryCodeSchema,
  estimatePasswordStrength,
  PASSWORD_MIN,
  PASSWORD_MAX,
} from "./schemas";
export type {
  RegisterFieldValues,
  LoginFieldValues,
  ForgotPasswordFieldValues,
  ResetPasswordFieldValues,
  PasswordStrength,
} from "./schemas";

export {
  useRegister,
  useRequestOtp,
  useVerifyOtp,
  useConsentPurposes,
  useLogin,
  useLogout,
  useAuthSessionQuery,
  useRequestPasswordReset,
  useConfirmPasswordReset,
  useVerifyLoginTotp,
  useVerifyLoginRecoveryCode,
  useSessions,
  useRevokeSession,
  useRevokeAllSessions,
  useProfile,
  useUpdateProfile,
  useChangePassword,
  useRequestEmailChange,
  useConfirmEmailChange,
  useIdentities,
  useUnlinkIdentity,
  useTotpStatus,
  useTotpInit,
  useTotpConfirm,
  useRegenerateRecoveryCodes,
  useDeleteTotp,
} from "./hooks";
export type { RegisterPayload } from "./hooks";

export { apiFetch, AuthApiError, readErrorParams } from "./api";

export { handlers, worker } from "./mocks";

export type {
  OtpPurpose,
  PendingRegistration,
  ConsentGrantDraft,
  ConsentPurposeOption,
  RegisterResponse,
  OtpRequestResponse,
  OtpVerifyResponse,
  LoginResponse,
  LoginMfaChallenge,
  LoginSuccess,
  TotpLoginVerifyResponse,
  TotpRecoveryResponse,
  SessionItem,
  SessionListResponse,
  ProfileResponse,
  UpdateProfileRequest,
  ChangePasswordRequest,
  EmailChangeRequestResponse,
  IdentityItem,
  IdentityListResponse,
  TotpStatusValue,
  TotpStatusResponse,
  TotpEnrollmentResponse,
  TotpConfirmResponse,
  TotpRegenerateResponse,
  SessionBootstrapResponse,
} from "./types";
export { isMfaChallenge } from "./types";
