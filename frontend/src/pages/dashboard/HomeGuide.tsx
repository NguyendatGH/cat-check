import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Camera, Check, FileText, FlaskConical, Sparkles, X } from "lucide-react";
import { Badge, Button } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";

export const HOME_GUIDE_STORAGE_KEY = "catcheck.home_guide_dismissed";

const ROADMAP_STEPS = [
  { key: "step1", icon: Camera },
  { key: "step2", icon: FlaskConical },
  { key: "step3", icon: FileText },
] as const;

export interface HomeGuideProps {
  catName?: string;
  className?: string;
}

export function HomeGuide({ catName, className }: HomeGuideProps) {
  const { t } = useTranslation(["onboarding", "common"]);
  const [isDismissed, setIsDismissed] = useState(true);

  useEffect(() => {
    try {
      const stored = localStorage.getItem(HOME_GUIDE_STORAGE_KEY);
      if (stored !== "true") {
        setIsDismissed(false);
      }
    } catch {
      setIsDismissed(false);
    }
  }, []);

  const handleDismiss = () => {
    try {
      localStorage.setItem(HOME_GUIDE_STORAGE_KEY, "true");
    } catch {
    }
    setIsDismissed(true);
  };

  if (isDismissed) return null;

  return (
    <aside
      aria-label={t("web.success.roadmap.title", { ns: "onboarding" })}
      className={cn(
        "relative overflow-hidden rounded-2xl border border-primary/20 bg-gradient-to-br from-primary/5 via-background-alt to-secondary/10 p-5 shadow-xs transition-all",
        className,
      )}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <Badge tone="brand" className="gap-1 font-semibold">
            <Sparkles className="size-3 text-white" aria-hidden="true" />
            {t("web.success.roadmap.badge", { ns: "onboarding" })}
          </Badge>
          <h2 className="text-body font-bold text-text-primary">
            {t("web.success.roadmap.title", { ns: "onboarding" })}
          </h2>
        </div>

        <button
          type="button"
          onClick={handleDismiss}
          aria-label={t("actions.cancel", { ns: "common" })}
          className="flex size-7 shrink-0 items-center justify-center rounded-full text-text-tertiary transition-colors hover:bg-chip-bg hover:text-text-primary"
        >
          <X className="size-4" aria-hidden="true" />
        </button>
      </div>

      <p className="mt-1.5 text-caption text-text-secondary">
        {t("web.success.roadmap.intro", {
          ns: "onboarding",
          catName: catName ?? t("dashboard.guestName", { ns: "common" }),
        })}
      </p>

      <div className="mt-4 grid grid-cols-1 gap-3 md:grid-cols-3">
        {ROADMAP_STEPS.map(({ key, icon: Icon }, index) => (
          <div
            key={key}
            className="flex items-start gap-3 rounded-xl border border-border/60 bg-surface/90 p-3.5 shadow-2xs backdrop-blur-xs"
          >
            <span className="flex size-7 shrink-0 items-center justify-center rounded-full bg-primary text-overline font-bold text-white">
              {index + 1}
            </span>
            <div className="flex min-w-0 flex-col">
              <div className="flex items-center gap-1.5">
                <Icon className="size-4 shrink-0 text-primary" aria-hidden="true" />
                <p className="text-caption font-bold text-text-primary">
                  {t(`web.success.roadmap.${key}.title`, { ns: "onboarding" })}
                </p>
              </div>
              <p className="mt-1 text-overline text-text-secondary leading-relaxed">
                {t(`web.success.roadmap.${key}.body`, { ns: "onboarding" })}
              </p>
            </div>
          </div>
        ))}
      </div>

      <div className="mt-4 flex flex-wrap items-center justify-between gap-3 border-t border-border/40 pt-3">
        <Link
          to="/scan/select-cat"
          className="flex h-10 items-center justify-center gap-1.5 rounded-xl bg-primary px-4 text-caption font-bold text-white shadow-brand-sm transition-colors hover:bg-primary-dark"
        >
          <Camera className="size-4" aria-hidden="true" />
          {t("dashboard.startScan", { ns: "common" })}
        </Link>
        <Button
          type="button"
          variant="tertiary"
          size="sm"
          leftIcon={<Check className="size-4" aria-hidden="true" />}
          onClick={handleDismiss}
        >
          {t("healthFlags.acknowledge", { ns: "common" })}
        </Button>
      </div>
    </aside>
  );
}
