import { useRef, useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { AlertTriangle, ChevronDown, ShieldCheck } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import type { DisclaimerVariant } from "./model";

export interface DisclaimerBannerProps {
  variant: DisclaimerVariant;
  defaultCollapsed?: boolean;
  hasWarningFlags?: boolean;
  className?: string;
}

export function DisclaimerBanner({
  variant,
  defaultCollapsed = variant === "emergency",
  hasWarningFlags = false,
  className,
}: DisclaimerBannerProps) {
  const { t } = useTranslation("legal");
  const [collapsed, setCollapsed] = useState(defaultCollapsed && !hasWarningFlags);

  if (variant === "footerLine") {
    return (
      <p className={cn("text-caption text-text-secondary", className)}>
        {t("disclaimer.footerLine.text")}{" "}
        <Link to="/legal/medical-disclaimer" className="font-semibold text-primary hover:underline">
          {t("disclaimer.footerLine.linkLabel")}
        </Link>
      </p>
    );
  }

  if (variant === "short") {
    return (
      <div
        className={cn(
          "flex items-start gap-2 rounded-lg border border-border bg-background-alt p-3 text-caption text-text-secondary",
          className,
        )}
      >
        <ShieldCheck className="mt-0.5 size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
        <p>{t("disclaimer.short.text")}</p>
      </div>
    );
  }

  if (variant === "medium") {
    return (
      <div
        className={cn(
          "flex flex-col gap-2 rounded-lg border border-border bg-background-alt p-4 text-caption text-text-secondary",
          className,
        )}
      >
        <div className="flex items-center gap-2 font-semibold text-text-primary">
          <ShieldCheck className="size-4 shrink-0" aria-hidden="true" />
          <span>{t("disclaimer.medium.title")}</span>
        </div>
        <p>{t("disclaimer.medium.text")}</p>
      </div>
    );
  }

  if (variant === "emergency") {
    const bullets = t("disclaimer.emergency.bullets", { returnObjects: true }) as string[];
    return (
      <div className={cn("overflow-hidden rounded-xl border-2 border-danger bg-danger-bg", className)} role="alert">
        <button
          type="button"
          onClick={() => {
            setCollapsed((v) => !v);
          }}
          aria-expanded={!collapsed}
          className="flex w-full min-h-11 items-center justify-between gap-2 p-3 text-left font-bold text-danger-text"
        >
          <span className="flex items-center gap-2">
            <AlertTriangle className="size-5 shrink-0" aria-hidden="true" />
            {t("disclaimer.emergency.title")}
          </span>
          <ChevronDown
            className={cn("size-5 shrink-0 transition-transform", !collapsed && "rotate-180")}
            aria-hidden="true"
          />
        </button>
        {!collapsed ? (
          <div className="flex flex-col gap-3 border-t border-danger/30 p-4 text-small text-danger-text">
            <p>{t("disclaimer.emergency.intro")}</p>
            <ul className="list-disc space-y-1 pl-5">
              {bullets.map((bullet) => (
                <li key={bullet}>{bullet}</li>
              ))}
            </ul>
            <p className="font-semibold">{t("disclaimer.emergency.closing")}</p>
          </div>
        ) : null}
      </div>
    );
  }

  const longBullets = t("disclaimer.long.bullets", { returnObjects: true }) as string[];
  return (
    <div className={cn("flex flex-col gap-4 rounded-xl border border-border bg-surface p-5", className)}>
      <div className="flex items-center gap-2">
        <ShieldCheck className="size-5 shrink-0 text-primary" aria-hidden="true" />
        <h2 className="text-h3 font-bold text-text-primary">{t("disclaimer.long.title")}</h2>
      </div>
      <p className="text-body text-text-secondary">{t("disclaimer.long.intro")}</p>
      <ul className="list-disc space-y-2 pl-5 text-body text-text-secondary">
        {longBullets.map((bullet) => (
          <li key={bullet}>{bullet}</li>
        ))}
      </ul>
      <p className="text-body text-text-secondary">{t("disclaimer.long.closing")}</p>
    </div>
  );
}


const BOTTOM_THRESHOLD_PX = 8;

export interface DisclaimerScrollProps {
  children: ReactNode;
  onReachBottom: () => void;
  label: string;
  hintText: string;
  doneText: string;
  className?: string;
}

export function DisclaimerScroll({
  children,
  onReachBottom,
  label,
  hintText,
  doneText,
  className,
}: DisclaimerScrollProps) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [reachedBottom, setReachedBottom] = useState(false);

  const handleScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const distanceToBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceToBottom <= BOTTOM_THRESHOLD_PX && !reachedBottom) {
      setReachedBottom(true);
      onReachBottom();
    }
  };

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div
        ref={scrollRef}
        onScroll={handleScroll}
        className="max-h-[50dvh] overflow-y-auto rounded-xl border border-border bg-surface p-4"
        role="region"
        aria-label={label}
      >
        {children}
      </div>
      <p className={cn("text-caption", reachedBottom ? "text-ph-normal-text" : "text-text-tertiary")}>
        {reachedBottom ? doneText : hintText}
      </p>
    </div>
  );
}
