import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { PartyPopper } from "lucide-react";
import { Button, Card } from "@/shared/ui";
import { isApiError } from "@/shared/api";
import {
  ActivationCodeField,
  isValidActivationCode,
  normalizeActivationCode,
  useActivateCode,
  type ActivationResult,
} from "@/features/credit";

/**
 * `/credits/activate` — p5 R1 · C05 (p9 §9.4.6: không có màn design gốc, dựng theo đặc tả
 * tối thiểu). Màn kích hoạt ĐỘC LẬP trong tài khoản/settings — KHÔNG phải bước onboarding
 * (`/onboarding/activate` thuộc `features/onboarding`, xem README).
 *
 * DESKTOP (>= lg): đây là tác vụ MỘT trường nhập, nên không chia hai cột như `/credits` —
 * cột hẹp giữa hộp nội dung 944px của `TaskLayout` là bố cục đúng. Thay vì để form trôi
 * trên nền trang, từ `lg` nó được đặt vào đúng chất liệu thẻ của `settings`
 * (`rounded-2xl bg-surface p-6 shadow-brand-md`) và nới từ 448px lên 560px cho cân với
 * chiều rộng cột nội dung ở các màn desktop khác.
 */
export function CreditsActivatePage() {
  const { t } = useTranslation(["credit", "common"]);
  const navigate = useNavigate();
  const activateCode = useActivateCode();

  const [code, setCode] = useState("");
  const [formatError, setFormatError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState<ActivationResult | null>(null);

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
      const result = await activateCode.mutateAsync(normalizeActivationCode(code));
      setSuccess(result);
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

  if (success) {
    return (
      <div className="mx-auto flex w-full max-w-md flex-col gap-6 lg:max-w-[560px]">
        <Card padding="lg" className="flex flex-col items-center gap-4 text-center lg:gap-5">
          <span className="flex size-16 items-center justify-center rounded-full bg-success-bg">
            <PartyPopper className="size-8 text-success-text" aria-hidden="true" />
          </span>
          <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("activate.success.title")}</h1>
          <dl className="flex w-full flex-col gap-3 text-left">
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.packageLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">{success.packageName}</dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.creditsLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">
                {t("activate.success.creditsValue", { credits: success.creditsGranted })}
              </dd>
            </div>
            <div className="flex items-center justify-between gap-4">
              <dt className="text-body text-text-secondary">{t("activate.success.expiresLabel")}</dt>
              <dd className="text-body font-semibold text-text-primary">
                {new Date(success.expiresAt).toLocaleDateString("vi-VN")}
              </dd>
            </div>
          </dl>
          <Button type="button" size="lg" className="w-full" onClick={() => { void navigate("/credits"); }}>
            {t("activate.success.continue")}
          </Button>
        </Card>
      </div>
    );
  }

  return (
    <div className="mx-auto flex w-full max-w-md flex-col gap-6 lg:max-w-[560px] lg:rounded-2xl lg:bg-surface lg:p-6 lg:shadow-brand-md">
      <div>
        <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.activate.title")}</h1>
        <p className="mt-1 text-caption text-text-secondary lg:text-body">{t("activate.subtitle")}</p>
      </div>

      <ActivationCodeField
        label={t("activate.inputLabel")}
        value={code}
        onChange={(value) => {
          setCode(value);
          setFormatError(null);
          setApiError(null);
        }}
        placeholder={t("activate.inputPlaceholder")}
        helperText={t("activate.formatHint")}
        error={formatError ?? undefined}
        disabled={isChecking}
      />

      {apiError ? (
        <div role="alert" className="rounded-lg border border-danger bg-danger-bg p-3">
          <p className="text-small font-medium text-danger-text">{apiError}</p>
        </div>
      ) : null}

      <div className="flex flex-col gap-3">
        <Button type="button" size="lg" loading={isChecking} disabled={isChecking} onClick={() => void onSubmit()}>
          {isChecking ? t("activate.checking") : t("activate.submit")}
        </Button>
        <Button type="button" variant="tertiary" size="md" disabled={isChecking} onClick={() => { void navigate(-1); }}>
          {t("actions.cancel", { ns: "common" })}
        </Button>
      </div>
    </div>
  );
}
