import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { PartyPopper } from "lucide-react";
import { Button, Card } from "@/shared/ui";
import {
  ActivationCodeInput,
  OnboardingShell,
  isValidActivationCode,
  toCanonicalCode,
  useActivateCode,
  useOnboardingStore,
} from "@/features/onboarding";
import { isApiError } from "@/shared/api";

/**
 * Bước 4/5 — Kích hoạt gói (p9 §9.4.6 C05: không có màn design gốc, dựng theo
 * đặc tả tối thiểu). Nhập mã `CC-<PKG>-<10 ký tự Crockford>` (p5 §5.9), validate
 * định dạng client-side, bỏ qua được (trial 3 lần — p5 R6).
 */
export function OnboardingActivatePage() {
  const { t } = useTranslation(["onboarding", "common"]);
  const navigate = useNavigate();
  const { setStep, setActivation } = useOnboardingStore();
  const activateCode = useActivateCode();

  const [code, setCode] = useState("");
  const [formatError, setFormatError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState<{
    packageName: string;
    credits: number;
    expiresAt: string;
  } | null>(null);

  const isChecking = activateCode.isPending;

  const onSubmit = async () => {
    setFormatError(null);
    setApiError(null);
    if (!code.trim()) {
      setFormatError(t("activate.required"));
      return;
    }
    if (!isValidActivationCode(code)) {
      setFormatError(t("activate.invalidFormat"));
      return;
    }
    try {
      const result = await activateCode.mutateAsync(toCanonicalCode(code));
      setActivation(result);
      setSuccess({
        packageName: result.batch.packageName,
        credits: result.batch.initialAmount,
        expiresAt: result.batch.expiresAt,
      });
    } catch (error) {
      if (isApiError(error)) {
        switch (error.code) {
          case "ACTIVATION_CODE_MALFORMED":
            setApiError(t("activate.errors.malformed"));
            break;
          case "ACTIVATION_CODE_INVALID":
            setApiError(t("activate.errors.invalid"));
            break;
          case "ACTIVATION_CODE_ALREADY_USED":
            setApiError(t("activate.errors.alreadyUsed"));
            break;
          case "ACTIVATION_CODE_EXPIRED":
            setApiError(t("activate.errors.expired"));
            break;
          default:
            setApiError(t("errors.generic"));
        }
      } else {
        setApiError(t("errors.generic"));
      }
    }
  };

  const onSkip = () => {
    setStep(5);
    void navigate("/onboarding/success");
  };

  const onContinueAfterSuccess = () => {
    setStep(5);
    void navigate("/onboarding/success");
  };

  if (success) {
    return (
      <OnboardingShell
        step={4}
        title={t("activate.title")}
        onBack={() => { void navigate("/onboarding/disclaimer"); }}
        onStepClick={(step) => {
          if (step === 1) { void navigate("/onboarding/cat"); }
          if (step === 2) { void navigate("/onboarding/health-survey"); }
          if (step === 3) { void navigate("/onboarding/disclaimer"); }
          if (step === 4) { void navigate("/onboarding/activate"); }
        }}
        footer={
          <Button type="button" size="lg" className="w-full" onClick={onContinueAfterSuccess}>
            {t("activate.success.continue")}
          </Button>
        }
      >
        <Card padding="lg" className="flex flex-col items-center gap-4 text-center">
          <span className="flex size-16 items-center justify-center rounded-full bg-success-bg">
            <PartyPopper className="size-8 text-success-text" aria-hidden="true" />
          </span>
          <h2 className="text-h2 font-bold text-text-primary">{t("activate.success.title")}</h2>
          <dl className="flex w-full flex-col gap-3 text-left">
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.packageLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">{success.packageName}</dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.creditsLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">
                {t("activate.success.creditsValue", { credits: success.credits })}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.expiresLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">
                {new Date(success.expiresAt).toLocaleDateString("vi-VN")}
              </dd>
            </div>
          </dl>
        </Card>
      </OnboardingShell>
    );
  }

  return (
    <OnboardingShell
      step={4}
      title={t("activate.title")}
      subtitle={t("activate.subtitle")}
      onBack={() => { void navigate("/onboarding/disclaimer"); }}
      onStepClick={(step) => {
        if (step === 1) { void navigate("/onboarding/cat"); }
        if (step === 2) { void navigate("/onboarding/health-survey"); }
        if (step === 3) { void navigate("/onboarding/disclaimer"); }
        if (step === 4) { void navigate("/onboarding/activate"); }
      }}
      footer={
        <div className="flex flex-col gap-3">
          {apiError ? (
            <div role="alert" className="rounded-lg border border-danger bg-danger-bg p-3">
              <p className="text-small font-medium text-danger-text">{apiError}</p>
            </div>
          ) : null}
          <Button
            type="button"
            size="lg"
            loading={isChecking}
            className="w-full"
            disabled={isChecking}
            onClick={() => void onSubmit()}
          >
            {isChecking ? t("activate.checking") : t("activate.submit")}
          </Button>
          <div className="flex flex-col items-center gap-1">
            <Button type="button" variant="tertiary" size="md" className="w-full" onClick={onSkip}>
              {t("activate.skip")}
            </Button>
            <p className="text-small text-text-tertiary">{t("activate.skipHint")}</p>
          </div>
        </div>
      }
    >
      <ActivationCodeInput
        value={code}
        onChange={(value) => {
          setCode(value);
          setFormatError(null);
          setApiError(null);
        }}
        error={formatError ?? undefined}
        disabled={isChecking}
      />
    </OnboardingShell>
  );
}
