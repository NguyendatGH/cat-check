import { useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, ChevronRight, X } from "lucide-react";
import { cn } from "@/shared/lib/cn";

const STORAGE_PREFIX = "catcheck:cat-alert-dismissed:";

function readDismissed(catId: string): string | null {
  try {
    return window.localStorage.getItem(STORAGE_PREFIX + catId);
  } catch {
    return null;
  }
}

function writeDismissed(catId: string, alertKey: string) {
  try {
    window.localStorage.setItem(STORAGE_PREFIX + catId, alertKey);
  } catch {
    // Storage unavailable: dismissal only lasts for this view.
  }
}

export interface CatEmergencyAlertProps {
  catId: string;
  flagCount: number;
  lastScanAt: string | null;
  onOpenDetails: () => void;
  className?: string;
}

export function CatEmergencyAlert({ catId, flagCount, lastScanAt, onOpenDetails, className }: CatEmergencyAlertProps) {
  const { t } = useTranslation("cat");
  const alertKey = `${String(flagCount)}:${lastScanAt ?? ""}`;
  const [dismissedKey, setDismissedKey] = useState(() => readDismissed(catId));

  if (flagCount <= 0) return null;
  const open = dismissedKey !== alertKey;

  return (
    <div
      className={cn(
        "grid transition-[grid-template-rows,opacity] duration-300 ease-[cubic-bezier(0.22,1,0.36,1)] motion-reduce:transition-none",
        open ? "grid-rows-[1fr] opacity-100" : "pointer-events-none grid-rows-[0fr] opacity-0",
        className,
      )}
      inert={!open}
    >
      <div className="min-h-0 overflow-hidden">
        <div role="alert" className="flex items-center gap-2 rounded-xl border border-danger/40 bg-danger-bg py-1.5 pl-3 pr-1.5">
          <AlertTriangle className="size-5 shrink-0 text-danger-text" aria-hidden="true" />
          <button
            type="button"
            onClick={onOpenDetails}
            className="flex min-h-11 min-w-0 flex-1 items-center gap-1 text-left"
          >
            <span className="min-w-0 flex-1">
              <span className="block truncate text-[13px] font-bold text-danger-text">{t("actions.emergencySigns")}</span>
              <span className="block truncate text-[12px] text-text-secondary">{t("alertBanner.recorded")}</span>
            </span>
            <span className="flex shrink-0 items-center text-[12px] font-semibold text-danger-text">
              {t("alertBanner.viewDetails")}
              <ChevronRight className="size-4" aria-hidden="true" />
            </span>
          </button>
          <button
            type="button"
            aria-label={t("alertBanner.dismiss")}
            onClick={() => {
              writeDismissed(catId, alertKey);
              setDismissedKey(alertKey);
            }}
            className="flex size-10 shrink-0 items-center justify-center rounded-lg text-text-tertiary transition-colors hover:bg-surface/70 hover:text-text-primary"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
      </div>
    </div>
  );
}
