import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Button } from "@/shared/ui";
import { Checkbox, DisclaimerScroll, OnboardingShell, useOnboardingStore } from "@/features/onboarding";

/**
 * Bước 3/5 — Miễn trừ trách nhiệm y tế (p15 §15.7.2 P1: D-LONG, bắt buộc tương tác,
 * không cho bỏ qua, không pre-check). Nội dung lấy từ p15 §15.7.3, nằm trong i18n.
 */
export function OnboardingDisclaimerPage() {
  const { t } = useTranslation(["onboarding", "common"]);
  const navigate = useNavigate();
  const { setStep } = useOnboardingStore();
  const [reachedBottom, setReachedBottom] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [ackError, setAckError] = useState(false);

  const content = t("disclaimer.content", { returnObjects: true }) as {
    heading: string;
    intro: string;
    points: string[];
    outro: string;
  };

  const onContinue = () => {
    if (!acknowledged) {
      setAckError(true);
      return;
    }
    setStep(4);
    void navigate("/onboarding/activate");
  };

  return (
    <OnboardingShell
      step={3}
      title={t("disclaimer.title")}
      subtitle={t("disclaimer.subtitle")}
      onBack={() => { void navigate("/onboarding/health-survey"); }}
      onStepClick={(step) => {
        if (step === 1) { void navigate("/onboarding/cat"); }
        if (step === 2) { void navigate("/onboarding/health-survey"); }
        if (step === 3) { void navigate("/onboarding/disclaimer"); }
      }}
      footer={
        <div className="flex flex-col gap-3">
          <div className="flex flex-col gap-1">
            <Checkbox
              checked={acknowledged}
              onChange={(checked) => {
                setAcknowledged(checked);
                setAckError(false);
              }}
              label={t("disclaimer.ack")}
              error={ackError ? t("disclaimer.ackRequired") : undefined}
              disabled={!reachedBottom}
            />
            {!reachedBottom ? (
              <p className="text-small text-text-tertiary">{t("disclaimer.scrollHint")}</p>
            ) : null}
          </div>
          <Button type="button" size="lg" className="w-full" onClick={onContinue}>
            {t("disclaimer.continue")}
          </Button>
        </div>
      }
    >
      <DisclaimerScroll onReachBottom={() => { setReachedBottom(true); }}>
        <article className="flex flex-col gap-4">
          <h2 className="text-h3 font-bold text-text-primary">{content.heading}</h2>
          <p className="text-body text-text-secondary">{content.intro}</p>
          <ul className="flex flex-col gap-3">
            {content.points.map((point, index) => (
              <li key={index} className="flex gap-3 text-body text-text-secondary">
                <span aria-hidden="true" className="mt-2 size-1.5 shrink-0 rounded-full bg-primary" />
                <span>{point}</span>
              </li>
            ))}
          </ul>
          <p className="text-body text-text-secondary">{content.outro}</p>
        </article>
      </DisclaimerScroll>
    </OnboardingShell>
  );
}
