import { z } from "zod";

/**
 * Zod schema cho form auth. Message truyền vào là KEY i18n (namespace `auth`) — dịch lúc
 * render qua `t(message)`, cùng quy ước với `features/onboarding/schemas.ts`.
 *
 * Độ dài mật khẩu 8–72 khớp `PasswordPolicy` backend (`RegisterRequest.password`
 * `@Size(min = 8, max = 72)`). Quy tắc "quá dễ đoán" (lặp ký tự / dãy số liên tiếp / nằm
 * trong danh sách bị chặn) chỉ kiểm tra được ở server (`PASSWORD_BREACHED`) — form chỉ
 * validate độ dài, còn lại hiển thị lỗi trả về từ API.
 */

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, "register.fields.fullName.required").max(120, "register.fields.fullName.tooLong"),
  // z.email() (zod v4) thay cho z.string().email() đã deprecated — gộp luôn "trống" và
  // "sai định dạng" vào một message vì zod báo cả hai lỗi cùng lúc cho input rỗng.
  email: z.email("register.fields.email.invalid").trim(),
  password: z
    .string()
    .min(PASSWORD_MIN, "register.fields.password.tooShort")
    .max(PASSWORD_MAX, "register.fields.password.tooLong"),
  referralCodeRaw: z.string().trim().max(64, "register.fields.referralCode.tooLong").optional().or(z.literal("")),
});

export type RegisterFieldValues = z.infer<typeof registerSchema>;

export const loginSchema = z.object({
  email: z.email("login.fields.email.invalid").trim(),
  password: z.string().min(1, "login.fields.password.required"),
  rememberMe: z.boolean(),
});

export type LoginFieldValues = z.infer<typeof loginSchema>;

export const otpCodeSchema = z
  .string()
  .trim()
  .regex(/^\d{6}$/, "verifyOtp.fields.code.invalid");

export const forgotPasswordSchema = z.object({
  email: z.email("forgotPassword.fields.email.invalid").trim(),
});

export type ForgotPasswordFieldValues = z.infer<typeof forgotPasswordSchema>;

export const resetPasswordSchema = z
  .object({
    newPassword: z
      .string()
      .min(PASSWORD_MIN, "resetPassword.fields.newPassword.tooShort")
      .max(PASSWORD_MAX, "resetPassword.fields.newPassword.tooLong"),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: "resetPassword.fields.confirmPassword.mismatch",
    path: ["confirmPassword"],
  });

export type ResetPasswordFieldValues = z.infer<typeof resetPasswordSchema>;

export const totpCodeSchema = z
  .string()
  .trim()
  .regex(/^\d{6}$/, "mfa.fields.code.invalid");

export const recoveryCodeSchema = z.string().trim().min(1, "mfa.fields.recoveryCode.required");

/**
 * Độ mạnh mật khẩu — chỉ heuristic hiển thị cho người dùng (không phải nguồn sự thật,
 * server mới quyết định chấp nhận hay không qua `PASSWORD_TOO_WEAK`/`PASSWORD_BREACHED`).
 * Không dùng thư viện ngoài (zxcvbn không nằm trong danh sách 91 version đã pin).
 */
export type PasswordStrength = "empty" | "weak" | "medium" | "strong";

export function estimatePasswordStrength(password: string): PasswordStrength {
  if (!password) return "empty";
  let score = 0;
  if (password.length >= PASSWORD_MIN) score += 1;
  if (password.length >= 12) score += 1;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password)) score += 1;
  if (/\d/.test(password)) score += 1;
  if (/[^A-Za-z0-9]/.test(password)) score += 1;
  if (score <= 2) return "weak";
  if (score <= 3) return "medium";
  return "strong";
}
