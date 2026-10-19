import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { loadSession, useSessionStore } from "@/entities/user";
import { playSplash } from "@/shared/lib/motion/splash";
import { ArrowRight, MailCheck, Pencil, ShieldCheck } from "lucide-react";
import { Button, EmptyState, Stepper } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import { WebRegisterSteps } from "./WebRegisterSteps";
import {
  OtpCodeInput,
  ResendCountdown,
  useAuthFlowStore,
  useRegister,
  useRequestOtp,
  useVerifyOtp,
  type OtpStatus,
} from "@/features/auth";
import { AuthCard } from "./AuthCard";

const INITIAL_RESEND_COOLDOWN_SECONDS = 60;
const OTP_LENGTH = 6;

function maskEmail(email: string): string {
  const [local, domain] = email.split("@");
  if (!domain) return "***";
  const maskedLocal = local.length <= 1 ? "*" : `${local[0]}***${local[local.length - 1]}`;
  return `${maskedLocal}@${domain}`;
}

export function VerifyOtpPage() {
  const { t } = useTranslation(["auth", "common"]);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const setSession = useSessionStore((state) => state.setSession);
  const pendingRegistration = useAuthFlowStore((s) => s.pendingRegistration);
  const reset = useAuthFlowStore((s) => s.reset);

  const verifyOtp = useVerifyOtp();
  const activateRegistration = useRegister();
  const requestOtp = useRequestOtp();

  const [code, setCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<OtpStatus>("idle");
  const [errorSignal, setErrorSignal] = useState(0);
  const [verifiedEmail, setVerifiedEmail] = useState<string | null>(null);
  const [resendSeconds, setResendSeconds] = useState(INITIAL_RESEND_COOLDOWN_SECONDS);
  const [resendKey, setResendKey] = useState(0);

  const email = pendingRegistration?.email ?? verifiedEmail;

  if (!email) {
    return (
      <AuthCard>
        <EmptyState
          title={t("verifyOtp.missingDraft.title")}
          description={t("verifyOtp.missingDraft.description")}
          action={
            <Button
              type="button"
              onClick={() => {
                void navigate("/auth/register");
              }}
            >
              {t("verifyOtp.missingDraft.cta")}
            </Button>
          }
        />
      </AuthCard>
    );
  }

  const isBusy = status === "verifying" || status === "success";

  const handleSubmit = async (submitted: string) => {
    if (!pendingRegistration || isBusy || submitted.length !== OTP_LENGTH) return;
    setError(null);
    setStatus("verifying");
    try {
      const { otpTicket } = await verifyOtp.mutateAsync({
        email: pendingRegistration.email,
        purpose: "REGISTER_VERIFY",
        code: submitted,
      });
      await activateRegistration.mutateAsync({
        email: pendingRegistration.email,
        password: pendingRegistration.password,
        fullName: pendingRegistration.fullName,
        referralCodeRaw: pendingRegistration.referralCodeRaw || undefined,
        otpTicket,
      });
      setVerifiedEmail(pendingRegistration.email);
      setStatus("success");
      reset();
      playSplash(loadSession(), (user) => {
        queryClient.removeQueries();
        setSession(user);
      });
    } catch (err) {
      setStatus("idle");
      setErrorSignal((n) => n + 1);
      if (isApiError(err) && err.code === "OTP_INVALID") {
        setError(t("verifyOtp.errors.invalidCode"));
      } else if (isApiError(err) && err.code === "OTP_EXPIRED") {
        setError(t("verifyOtp.errors.expired"));
      } else if (isApiError(err) && err.code === "OTP_LOCKED") {
        setError(t("verifyOtp.errors.locked"));
      } else if (isApiError(err) && err.code === "OTP_TICKET_INVALID") {
        setError(t("verifyOtp.errors.ticketInvalid"));
      } else {
        setError(t("errors.generic"));
      }
    }
  };

  const handleResend = async () => {
    setError(null);
    try {
      const result = await requestOtp.mutateAsync({ email, purpose: "REGISTER_VERIFY" });
      setResendSeconds(result.canResendInSeconds);
      setResendKey((k) => k + 1);
      setCode("");
      setStatus("idle");
    } catch {
      setError(t("errors.generic"));
    }
  };

  return (
    <div className="flex flex-col gap-6 pb-8 lg:gap-8">
      <WebRegisterSteps current={2} />

      <div className="mx-auto flex w-full max-w-[440px] flex-col gap-5">
        <div className="lg:hidden">
          <Stepper current={2} total={4} label={t("verifyOtp.stepLabel")} />
        </div>

        <section data-reveal className="flex flex-col gap-6 rounded-2xl bg-surface p-5 shadow-xs sm:p-8">
          <header className="flex flex-col items-center gap-3 text-center">
            <span className="flex size-16 items-center justify-center rounded-2xl bg-chip-bg text-primary-dark">
              <MailCheck size={28} aria-hidden="true" />
            </span>
            <div className="flex flex-col gap-1.5">
              <h1 className="text-h2 font-bold text-text-primary">{t("verifyOtp.title")}</h1>
              <p className="text-caption leading-[21px] text-text-secondary">{t("verifyOtp.subtitle")}</p>
            </div>
            <div className="flex max-w-full items-center gap-1 rounded-full bg-background-alt py-1 pl-3.5 pr-1">
              <span className="min-w-0 truncate text-caption font-bold text-text-primary">{maskEmail(email)}</span>
              <Link
                to="/auth/register"
                onClick={() => {
                  reset();
                }}
                className="flex min-h-8 shrink-0 items-center gap-1 rounded-full bg-surface px-2.5 text-small font-semibold text-primary-dark shadow-xs hover:bg-chip-bg"
              >
                <Pencil size={12} aria-hidden="true" />
                {t("verifyOtp.changeEmail")}
              </Link>
            </div>
          </header>

          <OtpCodeInput
            value={code}
            onChange={(next) => {
              setCode(next);
              if (error) setError(null);
            }}
            onComplete={(full) => {
              void handleSubmit(full);
            }}
            error={error ?? undefined}
            errorSignal={errorSignal}
            status={status}
            focusOnMount
          />

          <Button
            type="button"
            size="lg"
            className="gap-2 rounded-2xl"
            loading={status === "verifying"}
            disabled={code.length !== OTP_LENGTH || isBusy}
            onClick={() => {
              void handleSubmit(code);
            }}
          >
            {t("verifyOtp.submit")}
            <ArrowRight size={18} aria-hidden="true" />
          </Button>

          <div className="flex flex-col items-center gap-1 text-center">
            <ResendCountdown
              key={resendKey}
              seconds={resendSeconds}
              onResend={() => {
                void handleResend();
              }}
              disabled={requestOtp.isPending || isBusy}
            />
            <p className="text-[12px] text-text-tertiary">{t("verifyOtp.checkSpamNote")}</p>
          </div>
        </section>

        <p data-reveal className="flex items-start gap-2 px-1 text-[12px] leading-[18px] text-text-tertiary">
          <ShieldCheck size={14} aria-hidden="true" className="mt-0.5 shrink-0 text-primary-dark" />
          {t("verifyOtp.securityTip")}
        </p>

        <p data-reveal className="text-center text-caption text-text-secondary">
          {t("verifyOtp.supportPrompt")}{" "}
          <Link to="/legal/contact" className="font-semibold text-primary hover:underline">
            {t("verifyOtp.supportLink")}
          </Link>
        </p>
      </div>
    </div>
  );
}
