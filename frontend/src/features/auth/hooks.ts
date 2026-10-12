import { useMutation, useQuery, type UseMutationResult, type UseQueryResult } from "@tanstack/react-query";
import { apiFetch } from "./api";
import type {
  ChangePasswordRequest,
  ConsentGrantDraft,
  ConsentPurposeOption,
  EmailChangeRequestResponse,
  IdentityListResponse,
  LoginResponse,
  OtpPurpose,
  OtpRequestResponse,
  OtpVerifyResponse,
  ProfileResponse,
  RegisterResponse,
  SessionBootstrapResponse,
  SessionListResponse,
  TotpConfirmResponse,
  TotpEnrollmentResponse,
  TotpLoginVerifyResponse,
  TotpRecoveryResponse,
  TotpRegenerateResponse,
  TotpStatusResponse,
  UpdateProfileRequest,
} from "./types";



export interface RegisterPayload {
  email: string;
  password: string;
  fullName: string;
  referralCodeRaw?: string;
  locale?: string;
  otpTicket?: string;
  consents?: ConsentGrantDraft[];
}

export function useRegister(): UseMutationResult<RegisterResponse, Error, RegisterPayload> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<RegisterResponse>("/auth/register", {
        method: "POST",
        body: JSON.stringify({
          email: payload.email,
          password: payload.password,
          fullName: payload.fullName,
          referralCodeRaw: payload.referralCodeRaw || null,
          locale: payload.locale ?? "vi",
          otpTicket: payload.otpTicket ?? null,
          consents: payload.consents ?? [],
        }),
      }),
  });
}

export function useRequestOtp(): UseMutationResult<OtpRequestResponse, Error, { email: string; purpose: OtpPurpose }> {
  return useMutation({
    mutationFn: ({ email, purpose }) =>
      apiFetch<OtpRequestResponse>("/auth/otp/request", {
        method: "POST",
        body: JSON.stringify({ email, purpose }),
      }),
  });
}

export function useVerifyOtp(): UseMutationResult<
  OtpVerifyResponse,
  Error,
  { email: string; purpose: OtpPurpose; code: string }
> {
  return useMutation({
    mutationFn: ({ email, purpose, code }) =>
      apiFetch<OtpVerifyResponse>("/auth/otp/verify", {
        method: "POST",
        body: JSON.stringify({ email, purpose, code }),
      }),
  });
}

export function useConsentPurposes(): UseQueryResult<ConsentPurposeOption[]> {
  return useQuery({
    queryKey: ["auth", "consent-purposes"],
    queryFn: async () => {
      const page = await apiFetch<{ items: ConsentPurposeOption[] }>("/privacy/purposes?locale=vi");
      return page.items;
    },
    staleTime: 10 * 60 * 1000,
  });
}

export function useBuildVersion(): UseQueryResult<string | null> {
  return useQuery({
    queryKey: ["system", "status", "buildVersion"],
    queryFn: async () => {
      const status = await apiFetch<{ buildVersion?: string | null }>("/system/status");
      return status.buildVersion ?? null;
    },
    staleTime: 10 * 60 * 1000,
  });
}


export function useLogin(): UseMutationResult<
  LoginResponse,
  Error,
  { email: string; password: string; rememberMe: boolean }
> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<LoginResponse>("/auth/login", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}

export function useLogout(): UseMutationResult<undefined, Error, undefined> {
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/auth/logout", { method: "POST" }),
  });
}

export function useAuthSessionQuery(enabled: boolean): UseQueryResult<SessionBootstrapResponse> {
  return useQuery({
    queryKey: ["auth", "session-bootstrap"],
    queryFn: () => apiFetch<SessionBootstrapResponse>("/auth/session"),
    enabled,
    retry: false,
  });
}


export function useRequestPasswordReset(): UseMutationResult<undefined, Error, string> {
  return useMutation({
    mutationFn: (email) =>
      apiFetch<undefined>("/auth/password-reset/request", {
        method: "POST",
        body: JSON.stringify({ email }),
      }),
  });
}

export function useConfirmPasswordReset(): UseMutationResult<undefined, Error, { token: string; newPassword: string }> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<undefined>("/auth/password-reset/confirm", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}


export function useVerifyLoginTotp(): UseMutationResult<TotpLoginVerifyResponse, Error, string> {
  return useMutation({
    mutationFn: (code) =>
      apiFetch<TotpLoginVerifyResponse>("/auth/totp/verify", {
        method: "POST",
        body: JSON.stringify({ code }),
      }),
  });
}

export function useVerifyLoginRecoveryCode(): UseMutationResult<TotpRecoveryResponse, Error, string> {
  return useMutation({
    mutationFn: (recoveryCode) =>
      apiFetch<TotpRecoveryResponse>("/auth/totp/recovery", {
        method: "POST",
        body: JSON.stringify({ recoveryCode }),
      }),
  });
}


export function useSessions(enabled = true): UseQueryResult<SessionListResponse> {
  return useQuery({
    queryKey: ["auth", "sessions"],
    queryFn: () => apiFetch<SessionListResponse>("/auth/sessions"),
    enabled,
  });
}

export function useRevokeSession(): UseMutationResult<undefined, Error, string> {
  return useMutation({
    mutationFn: (sessionId) => apiFetch<undefined>(`/auth/sessions/${sessionId}`, { method: "DELETE" }),
  });
}

export function useRevokeAllSessions(): UseMutationResult<undefined, Error, undefined> {
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/auth/sessions/revoke-all", { method: "POST" }),
  });
}


export function useProfile(enabled = true): UseQueryResult<ProfileResponse> {
  return useQuery({
    queryKey: ["auth", "profile"],
    queryFn: () => apiFetch<ProfileResponse>("/users/me"),
    enabled,
  });
}

export function useUpdateProfile(): UseMutationResult<ProfileResponse, Error, UpdateProfileRequest> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<ProfileResponse>("/users/me", {
        method: "PATCH",
        body: JSON.stringify(payload),
      }),
  });
}


export const MY_AVATAR_PATH = "/api/v1/users/me/avatar";

export function useUploadMyAvatar(): UseMutationResult<{ avatarUrl: string }, Error, File> {
  return useMutation({
    mutationFn: (file) => {
      const form = new FormData();
      form.append("file", file);
      return apiFetch<{ avatarUrl: string }>("/users/me/avatar", { method: "PUT", body: form });
    },
  });
}

export function useRemoveMyAvatar(): UseMutationResult<undefined, Error, void> {
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/users/me/avatar", { method: "DELETE" }),
  });
}


export function useChangePassword(): UseMutationResult<undefined, Error, ChangePasswordRequest> {
  return useMutation({
    mutationFn: (payload) =>
      apiFetch<undefined>("/account/password", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
  });
}


export function useRequestEmailChange(): UseMutationResult<EmailChangeRequestResponse, Error, string> {
  return useMutation({
    mutationFn: (newEmail) =>
      apiFetch<EmailChangeRequestResponse>("/account/email-change/request", {
        method: "POST",
        body: JSON.stringify({ newEmail }),
      }),
  });
}

export function useConfirmEmailChange(): UseMutationResult<{ emailChanged: boolean }, Error, string> {
  return useMutation({
    mutationFn: (otpTicket) =>
      apiFetch<{ emailChanged: boolean }>("/account/email-change/confirm", {
        method: "POST",
        body: JSON.stringify({ otpTicket }),
      }),
  });
}


export function useIdentities(enabled = true): UseQueryResult<IdentityListResponse> {
  return useQuery({
    queryKey: ["auth", "identities"],
    queryFn: () => apiFetch<IdentityListResponse>("/account/identities"),
    enabled,
  });
}

export function useUnlinkIdentity(): UseMutationResult<undefined, Error, string> {
  return useMutation({
    mutationFn: (provider) => apiFetch<undefined>(`/account/identities/${provider}`, { method: "DELETE" }),
  });
}


export function useTotpStatus(enabled = true): UseQueryResult<TotpStatusResponse> {
  return useQuery({
    queryKey: ["auth", "totp-status"],
    queryFn: () => apiFetch<TotpStatusResponse>("/account/mfa/totp"),
    enabled,
  });
}

export function useTotpInit(): UseMutationResult<TotpEnrollmentResponse, Error, void> {
  return useMutation({
    mutationFn: () => apiFetch<TotpEnrollmentResponse>("/account/mfa/totp/init", { method: "POST" }),
  });
}

export function useTotpConfirm(): UseMutationResult<TotpConfirmResponse, Error, string> {
  return useMutation({
    mutationFn: (code) =>
      apiFetch<TotpConfirmResponse>("/account/mfa/totp/confirm", {
        method: "POST",
        body: JSON.stringify({ code }),
      }),
  });
}

export function useRegenerateRecoveryCodes(): UseMutationResult<TotpRegenerateResponse, Error, void> {
  return useMutation({
    mutationFn: () =>
      apiFetch<TotpRegenerateResponse>("/account/mfa/totp/recovery-codes/regenerate", { method: "POST" }),
  });
}

export function useDeleteTotp(): UseMutationResult<undefined, Error, undefined> {
  return useMutation({
    mutationFn: () => apiFetch<undefined>("/account/mfa/totp", { method: "DELETE" }),
  });
}
