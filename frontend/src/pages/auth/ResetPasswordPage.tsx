import { useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Button, EmptyState } from "@/shared/ui";
import { toast } from "sonner";
import { isApiError } from "@/shared/api";
import {
  InlineAlert,
  PasswordField,
  resetPasswordSchema,
  useAuthFlowStore,
  useConfirmPasswordReset,
  type ResetPasswordFieldValues,
} from "@/features/auth";
import { AuthCard } from "./AuthCard";

/**
 * `/auth/reset-password` (p9 §9.4.3 #6) — bước cuối quên mật khẩu. Cần `passwordResetTicket`
 * đã lấy ở `/auth/forgot-password` (otp_ticket, KHÔNG phải token trong URL — xem
 * `docs/handovers/A1-fe.md`). Đặt xong, backend thu hồi MỌI phiên (p11 §11.3.2) nên luôn
 * điều hướng về đăng nhập, không tự đăng nhập lại giúp người dùng.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const ticket = useAuthFlowStore((s) => s.passwordResetTicket);
  const reset = useAuthFlowStore((s) => s.reset);
  const confirmReset = useConfirmPasswordReset();

  const [submitError, setSubmitError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<ResetPasswordFieldValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { newPassword: "", confirmPassword: "" },
  });

  if (!ticket) {
    return (
      <AuthCard>
        <EmptyState
          title={t("resetPassword.missingTicket.title")}
          description={t("resetPassword.missingTicket.description")}
          action={
            <Button
              type="button"
              onClick={() => {
                void navigate("/auth/forgot-password");
              }}
            >
              {t("resetPassword.missingTicket.cta")}
            </Button>
          }
        />
      </AuthCard>
    );
  }

  const onSubmit = async (values: ResetPasswordFieldValues) => {
    setSubmitError(null);
    try {
      await confirmReset.mutateAsync({ token: ticket, newPassword: values.newPassword });
      reset();
      toast.success(t("resetPassword.successToast"));
      void navigate("/auth/login");
    } catch (error) {
      if (isApiError(error) && (error.code === "OTP_TICKET_INVALID" || error.code === "RESET_TOKEN_EXPIRED")) {
        setSubmitError(t("resetPassword.errors.ticketExpired"));
      } else if (isApiError(error) && error.code === "PASSWORD_TOO_WEAK") {
        setSubmitError(t("register.errors.passwordTooWeak"));
      } else if (isApiError(error) && error.code === "PASSWORD_BREACHED") {
        setSubmitError(t("register.errors.passwordBreached"));
      } else {
        setSubmitError(t("errors.generic"));
      }
    }
  };

  return (
    <AuthCard>
      <header className="flex flex-col gap-1 text-center">
        <h1 className="text-h2 font-bold text-text-primary">{t("resetPassword.title")}</h1>
        <p className="text-caption text-text-secondary">{t("resetPassword.subtitle")}</p>
      </header>

      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          void handleSubmit(onSubmit)(event);
        }}
        noValidate
      >
        <PasswordField
          label={t("resetPassword.fields.newPassword.label")}
          autoComplete="new-password"
          error={errors.newPassword ? t(errors.newPassword.message ?? "") : undefined}
          {...register("newPassword")}
        />
        <PasswordField
          label={t("resetPassword.fields.confirmPassword.label")}
          autoComplete="new-password"
          error={errors.confirmPassword ? t(errors.confirmPassword.message ?? "") : undefined}
          {...register("confirmPassword")}
        />

        {submitError ? <InlineAlert>{submitError}</InlineAlert> : null}

        <Button type="submit" size="lg" loading={confirmReset.isPending} disabled={confirmReset.isPending}>
          {t("resetPassword.submit")}
        </Button>
      </form>
    </AuthCard>
  );
}
