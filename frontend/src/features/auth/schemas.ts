import { z } from "zod";


export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, "register.fields.fullName.required").max(120, "register.fields.fullName.tooLong"),
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
