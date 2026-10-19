export { OtpCodeInput, type OtpStatus } from "./OtpCodeInput";
export {
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
  useBuildVersion,
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
  MY_AVATAR_PATH,
  useProfile,
  useRemoveMyAvatar,
  useUploadMyAvatar,
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
