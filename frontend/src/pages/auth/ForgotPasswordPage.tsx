import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { Button, Input } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  InlineAlert,
  OtpCodeInput,
  ResendCountdown,
  forgotPasswordSchema,
  useAuthFlowStore,
  useRequestOtp,
  useRequestPasswordReset,
  useVerifyOtp,
  type ForgotPasswordFieldValues,
} from "@/features/auth";

const INITIAL_RESEND_COOLDOWN_SECONDS = 60;

/**
 * `/auth/forgot-password` (p9 §9.4.3 #5). Backend không có luồng "link trong email" —
 * `p11 §11.2.6`/`PasswordResetService` xác nhận đặt lại mật khẩu đi qua OTP + ticket
 * (giống đăng ký), KHÔNG phải token trong URL như M1 dự tính ban đầu. Màn này vì vậy gộp
 * 2 bước: nhập email -> nhập mã OTP; ticket lấy được mang sang `/auth/reset-password`
 * qua store (không đưa vào query string).
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const setPasswordResetEmail = useAuthFlowStore((s) => s.setPasswordResetEmail);
  const setPasswordResetTicket = useAuthFlowStore((s) => s.setPasswordResetTicket);

  const requestReset = useRequestPasswordReset();
  const requestOtp = useRequestOtp();
  const verifyOtp = useVerifyOtp();

  const [step, setStep] = useState<"EMAIL" | "OTP">("EMAIL");
  const [email, setEmail] = useState("");
  const [code, setCode] = useState("");
  const [otpError, setOtpError] = useState<string | null>(null);
  const [resendSeconds, setResendSeconds] = useState(INITIAL_RESEND_COOLDOWN_SECONDS);
  const [resendKey, setResendKey] = useState(0);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ForgotPasswordFieldValues>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const onSubmitEmail = async (values: ForgotPasswordFieldValues) => {
    // A8 luôn 202 kể cả email không tồn tại (không lộ email có đăng ký hay không) — vì
    // vậy KHÔNG hiện lỗi theo response, luôn chuyển bước tiếp theo.
    await requestReset.mutateAsync(values.email);
    setEmail(values.email);
    setPasswordResetEmail(values.email);
    setStep("OTP");
  };

  const handleVerify = async () => {
    setOtpError(null);
    try {
      const { otpTicket } = await verifyOtp.mutateAsync({ email, purpose: "PASSWORD_RESET", code });
      setPasswordResetTicket(otpTicket);
      void navigate("/auth/reset-password");
    } catch (error) {
      if (isApiError(error) && error.code === "OTP_INVALID") {
        setOtpError(t("verifyOtp.errors.invalidCode"));
      } else if (isApiError(error) && error.code === "OTP_EXPIRED") {
        setOtpError(t("verifyOtp.errors.expired"));
      } else if (isApiError(error) && error.code === "OTP_LOCKED") {
        setOtpError(t("verifyOtp.errors.locked"));
      } else {
        setOtpError(t("errors.generic"));
      }
    }
  };

  const handleResend = async () => {
    setOtpError(null);
    try {
      const result = await requestOtp.mutateAsync({ email, purpose: "PASSWORD_RESET" });
      setResendSeconds(result.canResendInSeconds);
      setResendKey((k) => k + 1);
      setCode("");
    } catch {
      setOtpError(t("errors.generic"));
    }
  };

  if (step === "OTP") {
    return (
      <div className="flex flex-col gap-6">
        <header className="flex flex-col gap-1 text-center">
          <h1 className="text-h2 font-bold text-text-primary">{t("forgotPassword.otpStep.title")}</h1>
          <p className="text-caption text-text-secondary">{t("forgotPassword.otpStep.subtitle", { email })}</p>
        </header>

        <OtpCodeInput
          value={code}
          onChange={setCode}
          error={otpError ?? undefined}
          disabled={verifyOtp.isPending}
          focusOnMount
        />

        <div className="flex justify-center">
          <ResendCountdown
            key={resendKey}
            seconds={resendSeconds}
            onResend={() => {
              void handleResend();
            }}
            disabled={requestOtp.isPending}
          />
        </div>

        <Button
          type="button"
          size="lg"
          loading={verifyOtp.isPending}
          disabled={code.length !== 6 || verifyOtp.isPending}
          onClick={() => {
            void handleVerify();
          }}
        >
          {t("forgotPassword.otpStep.submit")}
        </Button>

        <button
          type="button"
          onClick={() => {
            setStep("EMAIL");
          }}
          className="min-h-11 text-caption text-text-tertiary hover:underline"
        >
          {t("forgotPassword.otpStep.back")}
        </button>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-col gap-1 text-center">
        <h1 className="text-h2 font-bold text-text-primary">{t("forgotPassword.title")}</h1>
        <p className="text-caption text-text-secondary">{t("forgotPassword.subtitle")}</p>
      </header>

      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void handleSubmit(onSubmitEmail)(event);
        }}
        noValidate
      >
        <Input
          type="email"
          label={t("forgotPassword.fields.email.label")}
          autoComplete="email"
          placeholder={t("forgotPassword.fields.email.placeholder")}
          error={errors.email ? t(errors.email.message ?? "") : undefined}
          {...register("email")}
        />

        {requestReset.isError ? <InlineAlert>{t("errors.generic")}</InlineAlert> : null}

        <Button type="submit" size="lg" loading={requestReset.isPending} disabled={requestReset.isPending}>
          {t("forgotPassword.submit")}
        </Button>
      </form>

      <p className="text-center text-caption text-text-secondary">
        <Link to="/auth/login" className="font-semibold text-primary hover:underline">
          {t("forgotPassword.backToLogin")}
        </Link>
      </p>
    </div>
  );
}
