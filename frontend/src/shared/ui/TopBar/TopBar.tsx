import type { ReactNode } from "react";
import { ArrowLeft } from "lucide-react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import appLogo from "@/shared/assets/images/web-auth/logo.png";

export interface TopBarProps {
  variant: "app-level" | "task-level";
  title?: string;
  onBack?: () => void;
  actions?: ReactNode;
}

const HEADER_SHELL = "sticky top-0 z-[var(--z-sticky-header)] w-full bg-background/80 shadow-xs backdrop-blur-md";

export function TopBar({ variant, title, onBack, actions }: TopBarProps) {
  const { t } = useTranslation("common");
  const navigate = useNavigate();

  const handleBack = () => {
    if (onBack) {
      onBack();
      return;
    }
    const state = typeof window !== "undefined" ? (window.history.state as { idx?: number } | null) : null;
    const canGoBack = typeof state?.idx === "number" && state.idx > 0;

    if (canGoBack) {
      void navigate(-1);
    } else {
      void navigate("/");
    }
  };

  if (variant === "app-level") {
    return (
      <header className={HEADER_SHELL}>
        <div className="flex h-14 items-center justify-between px-4">
          <Link to="/" className="inline-flex shrink-0 items-center" aria-label={t("app.name")}>
            <img src={appLogo} alt={t("app.name")} className="h-11 w-[122px] object-contain object-left" />
          </Link>
          <div className="flex items-center gap-2">{actions}</div>
        </div>
      </header>
    );
  }

  return (
    <header className={cn(HEADER_SHELL, "border-b border-primary/10")}>
      <div className="mx-auto flex h-14 max-w-[480px] items-center justify-between px-2 sm:px-3">
        <div className="flex min-w-0 items-center gap-1.5 text-primary-dark">
          <button
            type="button"
            onClick={handleBack}
            aria-label={t("actions.back")}
            className="flex size-10 shrink-0 items-center justify-center rounded-full text-text-primary transition-colors hover:bg-background-alt"
          >
            <ArrowLeft size={20} aria-hidden="true" />
          </button>
          <Link to="/" className="inline-flex shrink-0 items-center" aria-label={t("app.name")}>
            <img src={appLogo} alt={t("app.name")} className="h-10 w-[112px] object-contain object-left" />
          </Link>
          {title ? (
            <span className="truncate border-l border-border pl-2 text-xs font-semibold text-text-tertiary">
              {title}
            </span>
          ) : null}
        </div>
        {actions ? (
          <div className="flex size-10 items-center justify-center">{actions}</div>
        ) : (
          <span className="size-10" />
        )}
      </div>
    </header>
  );
}
