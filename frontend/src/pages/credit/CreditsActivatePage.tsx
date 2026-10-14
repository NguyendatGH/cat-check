import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import {
  ArrowRight,
  Check,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  ClipboardPaste,
  HelpCircle,
  MessageCircle,
  PartyPopper,
  ScanLine,
  ShieldAlert,
  ShoppingBag,
  Sparkles,
  Ticket,
  X,
} from "lucide-react";
import { Badge, Button, Card, SkeletonLoader, celebrate } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import {
  ActivationCodeField,
  isValidActivationCode,
  formatActivationCode,
  useActivateCode,
  useCreditBalance,
  usePackageCatalog,
  type ActivationResult,
  type PackagePlan,
} from "@/features/credit";
import { usePageReveal } from "@/shared/lib/motion/usePageReveal";

export function CreditsActivatePage() {
  const { t } = useTranslation(["credit", "common"]);
  const navigate = useNavigate();
  const activateCode = useActivateCode();
  const { data: balance } = useCreditBalance();

  const pageRef = useRef<HTMLDivElement>(null);
  usePageReveal(pageRef);

  const [code, setCode] = useState("");
  const [formatError, setFormatError] = useState<string | null>(null);
  const [apiError, setApiError] = useState<string | null>(null);
  const [success, setSuccess] = useState<ActivationResult | null>(null);
  const [showGuide, setShowGuide] = useState(true);
  const [copiedFeedback, setCopiedFeedback] = useState(false);

  const isChecking = activateCode.isPending;
  const isCodeValid = isValidActivationCode(code);

  const handlePaste = async () => {
    try {
      const text = await navigator.clipboard.readText();
      if (text) {
        setCode(formatActivationCode(text));
        setFormatError(null);
        setApiError(null);
        setCopiedFeedback(true);
        setTimeout(() => {
          setCopiedFeedback(false);
        }, 2000);
      }
    } catch {
      // Clipboard access denied or unavailable: the user can still type the code.
    }
  };

  const handleClear = () => {
    setCode("");
    setFormatError(null);
    setApiError(null);
  };

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
      const result = await activateCode.mutateAsync(formatActivationCode(code));
      setSuccess(result);
      celebrate();
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
      <div ref={pageRef} className="mx-auto flex w-full max-w-md flex-col gap-6 lg:max-w-[560px]">
        <Card data-reveal padding="lg" elevated className="relative flex flex-col items-center gap-5 overflow-hidden text-center">
          <div
            className="pointer-events-none absolute -top-12 size-48 rounded-full bg-success/20 blur-3xl animate-pulse-glow"
            aria-hidden="true"
          />

          <div className="relative pt-2">
            <span className="flex size-20 items-center justify-center rounded-full bg-success-bg ring-8 ring-success-bg/60 animate-bounce">
              <PartyPopper className="size-10 text-success-text" aria-hidden="true" />
            </span>
          </div>

          <div>
            <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("activate.success.title")}</h1>
            <p className="mt-1 text-caption text-text-secondary">
              {t("activate.success.statusReady")}
            </p>
          </div>

          <div className="shimmer-effect flex w-full flex-col items-center justify-center rounded-2xl bg-gradient-to-r from-primary to-primary-dark p-5 text-white shadow-brand-md animate-pop-spring">
            <span className="text-overline font-semibold uppercase tracking-wider text-on-primary-subtle">
              {t("activate.success.creditsLabel")}
            </span>
            <span className="text-display font-black tracking-tight text-white drop-shadow-sm">
              {t("activate.success.creditsValue", { credits: success.creditsGranted })}
            </span>
          </div>

          <div className="w-full rounded-xl border border-dashed border-border-strong bg-background-alt p-4">
            <dl className="flex flex-col gap-3 text-left">
              <div className="flex items-center justify-between gap-4 border-b border-border/40 pb-2.5">
                <dt className="text-caption font-medium text-text-secondary">{t("activate.success.packageLabel")}</dt>
                <dd>
                  <Badge tone="brand">{success.packageName}</Badge>
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4 border-b border-border/40 pb-2.5">
                <dt className="text-caption font-medium text-text-secondary">{t("activate.success.expiresLabel")}</dt>
                <dd className="text-caption font-semibold text-text-primary">
                  {new Date(success.expiresAt).toLocaleDateString("vi-VN")}
                </dd>
              </div>
              <div className="flex items-center justify-between gap-4">
                <dt className="text-caption font-medium text-text-secondary">{t("activate.success.statusLabel")}</dt>
                <dd>
                  <Badge tone="success">{t("activate.success.statusReady")}</Badge>
                </dd>
              </div>
            </dl>
          </div>

          <div className="flex w-full flex-col gap-3 pt-2">
            <Button
              type="button"
              size="lg"
              className="shimmer-effect w-full font-bold shadow-brand-md transition-all duration-200 hover:shadow-brand-lg active:scale-[0.99]"
              leftIcon={<ArrowRight className="size-5" aria-hidden="true" />}
              onClick={() => {
                void navigate("/scan/select-cat");
              }}
            >
              {t("activate.success.scanNow")}
            </Button>
            <Button
              type="button"
              variant="tertiary"
              size="md"
              className="w-full transition-colors hover:bg-background-alt"
              onClick={() => {
                void navigate("/credits");
              }}
            >
              {t("activate.success.continue")}
            </Button>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div ref={pageRef} className="mx-auto flex w-full max-w-md flex-col gap-5 pb-8 lg:max-w-[560px] lg:gap-6">
      <div
        data-reveal
        className="relative overflow-hidden rounded-2xl border border-primary/15 bg-gradient-to-br from-primary/10 via-background-alt to-secondary/15 p-5 shadow-xs"
      >
        <div
          className="pointer-events-none absolute -right-8 -top-8 size-40 rounded-full bg-secondary/25 blur-2xl animate-pulse-glow"
          aria-hidden="true"
        />
        <div
          className="pointer-events-none absolute -bottom-8 -left-8 size-40 rounded-full bg-primary/20 blur-2xl animate-pulse-glow"
          aria-hidden="true"
        />

        <div className="relative flex items-start justify-between gap-3">
          <div className="flex items-center gap-3.5">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-2xl bg-gradient-to-tr from-primary-dark to-primary text-white shadow-brand-md ring-4 ring-primary/15 animate-float-slow">
              <Ticket className="size-6 text-white" aria-hidden="true" />
            </span>
            <div>
              <h1 className="text-h2 font-bold text-text-primary lg:text-h1">{t("pages.activate.title")}</h1>
              <p className="mt-0.5 text-caption text-text-secondary">{t("activate.subtitle")}</p>
            </div>
          </div>
        </div>

        {balance ? (
          <div className="relative mt-4 flex items-center justify-between rounded-xl border border-primary/10 bg-surface/90 px-3.5 py-2.5 backdrop-blur-xs transition-shadow hover:shadow-xs">
            <div className="flex items-center gap-2">
              <Sparkles className="size-4 text-primary animate-pulse" aria-hidden="true" />
              <span className="text-caption font-medium text-text-secondary">{t("activate.currentBalance")}</span>
            </div>
            <span className="text-caption font-bold text-primary">
              {balance.availableBalance} {t("balance.unit")}
            </span>
          </div>
        ) : null}
      </div>

      <Card
        data-reveal
        padding="md"
        className="motion-card flex flex-col gap-4 rounded-2xl border border-border/70 bg-surface p-5 shadow-xs transition-all duration-300 focus-within:border-primary/60 focus-within:ring-4 focus-within:ring-primary/10 lg:p-6"
      >
        <div className="flex items-center justify-between gap-2">
          <label htmlFor="activation-code-field" className="text-caption font-bold text-text-primary">
            {t("activate.inputLabel")}
          </label>
          {isCodeValid ? (
            <span className="animate-pop-spring">
              <Badge tone="success" className="gap-1 font-semibold">
                <CheckCircle2 className="size-3.5" aria-hidden="true" />
                {t("activate.formatValid")}
              </Badge>
            </span>
          ) : null}
        </div>

        <div>
          <ActivationCodeField
            id="activation-code-field"
            value={code}
            onChange={(value) => {
              setCode(value);
              setFormatError(null);
              setApiError(null);
            }}
            placeholder={t("activate.inputPlaceholder")}
            error={formatError ?? undefined}
            disabled={isChecking}
            className="font-mono text-base font-bold uppercase tracking-wider text-text-primary"
            leftIcon={<Ticket className="size-5 text-primary" aria-hidden="true" />}
            rightSlot={
              code ? (
                <button
                  type="button"
                  onClick={handleClear}
                  aria-label={t("activate.clear")}
                  className="mr-1 flex size-7 items-center justify-center rounded-full text-text-tertiary transition-colors hover:bg-chip-bg hover:text-text-primary active:scale-90"
                >
                  <X className="size-4" aria-hidden="true" />
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => {
                    void handlePaste();
                  }}
                  className={cn(
                    "mr-1 flex items-center gap-1 rounded-lg px-2.5 py-1 text-overline font-semibold transition-all active:scale-95",
                    copiedFeedback
                      ? "bg-success-bg text-success-text animate-pop-spring"
                      : "bg-chip-bg text-primary hover:bg-primary/10",
                  )}
                >
                  <ClipboardPaste className="size-3.5" aria-hidden="true" />
                  <span>{copiedFeedback ? t("activate.pasted") : t("activate.paste")}</span>
                </button>
              )
            }
          />

          <div className="mt-2 flex flex-wrap items-center justify-between gap-2 text-caption text-text-tertiary">
            <span>{t("activate.formatHint")}</span>
            <button
              type="button"
              onClick={() => {
                setCode("CC-PLUS-7K3M9QX2RT");
                setFormatError(null);
                setApiError(null);
              }}
              className="text-overline font-mono font-medium text-primary transition-colors hover:underline active:scale-95"
            >
              {t("activate.exampleCode")}
            </button>
          </div>
        </div>

        {apiError ? (
          <div role="alert" className="flex items-start gap-2.5 rounded-xl border border-danger/40 bg-danger-bg p-3.5 text-danger-text animate-pop-spring">
            <ShieldAlert className="mt-0.5 size-5 shrink-0 text-danger" aria-hidden="true" />
            <p className="text-caption font-medium">{apiError}</p>
          </div>
        ) : null}

        <div className="flex flex-col gap-3 pt-1">
          <Button
            type="button"
            size="lg"
            className="shimmer-effect w-full font-bold shadow-brand-md transition-all duration-200 hover:shadow-brand-lg active:scale-[0.99]"
            loading={isChecking}
            disabled={isChecking}
            leftIcon={<Sparkles className="size-5 transition-transform group-hover:rotate-12" aria-hidden="true" />}
            onClick={() => void onSubmit()}
          >
            {isChecking ? t("activate.checking") : t("activate.submit")}
          </Button>
          <Button
            type="button"
            variant="tertiary"
            size="md"
            disabled={isChecking}
            className="transition-colors hover:bg-background-alt"
            onClick={() => {
              void navigate(-1);
            }}
          >
            {t("actions.cancel", { ns: "common" })}
          </Button>
        </div>
      </Card>

      <div data-reveal className="overflow-hidden rounded-2xl border border-border/70 bg-surface shadow-xs transition-all">
        <button
          type="button"
          onClick={() => {
            setShowGuide((prev) => !prev);
          }}
          className="flex w-full items-center justify-between p-4 text-left transition-colors hover:bg-background-alt"
        >
          <div className="flex items-center gap-2.5">
            <HelpCircle className="size-5 shrink-0 text-primary" aria-hidden="true" />
            <span className="text-body font-bold text-text-primary">{t("activate.guideTitle")}</span>
          </div>
          <span className="text-text-tertiary transition-transform duration-300">
            {showGuide ? <ChevronUp className="size-5" aria-hidden="true" /> : <ChevronDown className="size-5" aria-hidden="true" />}
          </span>
        </button>

        <div
          className={cn(
            "grid transition-all duration-300 ease-in-out",
            showGuide ? "grid-rows-[1fr] opacity-100" : "grid-rows-[0fr] opacity-0",
          )}
        >
          <div className="overflow-hidden border-t border-border/40 p-4 pt-3">
            <div className="flex flex-col gap-3">
              <div className="group flex items-start gap-3 rounded-xl bg-background-alt p-3 transition-all duration-200 hover:bg-surface hover:shadow-xs">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-overline font-bold text-white transition-transform duration-200 group-hover:scale-110">
                  1
                </span>
                <div>
                  <p className="text-caption font-bold text-text-primary">{t("activate.guideStep1Title")}</p>
                  <p className="text-overline text-text-secondary">{t("activate.guideStep1Desc")}</p>
                </div>
              </div>

              <div className="group flex items-start gap-3 rounded-xl bg-background-alt p-3 transition-all duration-200 hover:bg-surface hover:shadow-xs">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-overline font-bold text-white transition-transform duration-200 group-hover:scale-110">
                  2
                </span>
                <div>
                  <p className="text-caption font-bold text-text-primary">{t("activate.guideStep2Title")}</p>
                  <p className="text-overline text-text-secondary">{t("activate.guideStep2Desc")}</p>
                </div>
              </div>

              <div className="group flex items-start gap-3 rounded-xl bg-background-alt p-3 transition-all duration-200 hover:bg-surface hover:shadow-xs">
                <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-overline font-bold text-white transition-transform duration-200 group-hover:scale-110">
                  3
                </span>
                <div>
                  <p className="text-caption font-bold text-text-primary">{t("activate.guideStep3Title")}</p>
                  <p className="text-overline text-text-secondary">{t("activate.guideStep3Desc")}</p>
                </div>
              </div>
            </div>

            <div className="mt-3 flex items-center justify-between rounded-xl border border-secondary/40 bg-secondary/10 p-3 transition-colors hover:bg-secondary/15">
              <div>
                <p className="text-caption font-semibold text-text-primary">{t("activate.shopPrompt")}</p>
                <p className="text-overline text-text-secondary">{t("activate.shopCta")}</p>
              </div>
              <Button
                type="button"
                variant="secondary"
                size="sm"
                className="shrink-0 font-semibold transition-transform active:scale-95"
                leftIcon={<ShoppingBag className="size-4" aria-hidden="true" />}
                onClick={() => {
                  void navigate("/shop");
                }}
              >
                {t("activate.shopCta")}
              </Button>
            </div>
          </div>
        </div>
      </div>

      <section data-reveal className="flex flex-col gap-3">
        <h2 className="text-overline font-bold uppercase tracking-wider text-text-tertiary">
          {t("activate.perksTitle")}
        </h2>
        <PackagePerks />
      </section>
    </div>
  );
}

// Mirrors backend AiChatRateLimitRule.AI_CHAT_BY_USER ("20/1h"); chat does not consume credits.
const AI_CHAT_MESSAGES_PER_HOUR = 20;

function PackagePerks() {
  const { t } = useTranslation("credit");
  const { data: packages, isPending, isError } = usePackageCatalog();

  const featuresOf = (plan: PackagePlan) =>
    [
      plan.historyLevel === "ADVANCED"
        ? t("activate.perks.historyAdvanced")
        : plan.historyLevel === "BASIC"
          ? t("activate.perks.historyBasic")
          : null,
      plan.hasTrend ? t("activate.perks.trend") : null,
      plan.hasReminder ? t("activate.perks.reminder") : null,
      plan.hasExport ? t("activate.perks.export") : null,
      plan.storeImage ? t("activate.perks.storeImage") : null,
      plan.maxCatProfiles === null
        ? t("activate.perks.catsUnlimited")
        : t("activate.perks.cats", { count: plan.maxCatProfiles }),
    ].filter((item): item is string => item !== null);

  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-start gap-3.5 rounded-2xl border border-primary/20 bg-primary/5 p-3.5">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary">
          <MessageCircle className="size-5" aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="text-body font-bold text-text-primary">{t("activate.perks.aiTitle")}</p>
          <p className="mt-0.5 text-caption leading-relaxed text-text-secondary">
            {t("activate.perks.aiDesc", { count: AI_CHAT_MESSAGES_PER_HOUR })}
          </p>
        </div>
      </div>

      {isPending ? (
        <SkeletonLoader shape="card" />
      ) : isError ? (
        <p className="text-caption text-text-secondary">{t("activate.perks.loadError")}</p>
      ) : (
        packages.map((plan) => (
          <div key={plan.code} className="flex flex-col gap-2.5 rounded-2xl border border-border/60 bg-surface p-3.5 shadow-xs">
            <div className="flex items-start justify-between gap-3">
              <p className="min-w-0 text-body font-bold text-text-primary">{plan.name}</p>
              <span className="flex shrink-0 items-center gap-1 rounded-full bg-secondary-light px-2.5 py-1 text-caption font-bold text-secondary-text-on">
                <ScanLine className="size-3.5" aria-hidden="true" />
                {t("activate.perks.scans", { count: plan.creditAmount })}
              </span>
            </div>
            <p className="text-caption text-text-secondary">
              {t("activate.perks.validity", { count: plan.creditValidityDays })}
            </p>
            <ul className="flex flex-wrap gap-1.5">
              {featuresOf(plan).map((feature) => (
                <li
                  key={feature}
                  className="flex items-center gap-1 rounded-full bg-chip-bg px-2.5 py-1 text-small font-medium text-text-primary"
                >
                  <Check className="size-3 text-primary" aria-hidden="true" />
                  {feature}
                </li>
              ))}
            </ul>
          </div>
        ))
      )}
    </div>
  );
}
