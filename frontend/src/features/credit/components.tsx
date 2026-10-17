import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { ArrowDown, ArrowUp, Check, X } from "lucide-react";
import { Badge, Input, type InputProps } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { formatActivationCode } from "./schemas";
import { usePackageCatalog } from "./hooks";
import type { CreditBatch, CreditLedgerType, Entitlement, LedgerEntry } from "./types";
import { WELCOME_PACKAGE_CODE, isWelcomeBatch } from "./welcome";


const SECONDS_PER_DAY = 86_400;
const SECONDS_PER_HOUR = 3_600;

export function formatRemainingSeconds(seconds: number, t: TFunction<"credit">): string {
  if (seconds <= 0) return t("batch.expired");
  const days = Math.floor(seconds / SECONDS_PER_DAY);
  const hours = Math.floor((seconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR);
  if (days > 0) return t("batch.remainingDaysHours", { days, hours });
  if (hours > 0) return t("batch.remainingHours", { hours });
  return t("batch.remainingSoon");
}


export interface CreditBatchCardProps {
  batch: CreditBatch;
  className?: string;
}

export function CreditBatchCard({ batch, className }: CreditBatchCardProps) {
  const { t } = useTranslation("credit");
  const { data: plans } = usePackageCatalog();
  const percent = batch.initialAmount > 0 ? Math.round((batch.remainingAmount / batch.initialAmount) * 100) : 0;
  const expired = batch.remainingSeconds <= 0;

  return (
    <div
      data-plan={batch.packageCode}
      className={cn("flex flex-col gap-3 rounded-2xl bg-surface p-4 ring-1 ring-border/60", className)}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex min-w-0 flex-col">
          <p className="truncate text-body font-bold text-text-primary">
            {isWelcomeBatch(batch)
              ? t("welcome.batchName")
              : (plans?.find((plan) => plan.code === batch.packageCode)?.name ?? batch.packageCode)}
          </p>
          <p className="text-small text-text-tertiary">
            {t("batch.activatedAt", { date: new Date(batch.activatedAt).toLocaleDateString("vi-VN") })}
          </p>
        </div>
        <Badge tone={expired ? "neutral" : "brand"} className="shrink-0">
          {expired ? t("batch.expired") : formatRemainingSeconds(batch.remainingSeconds, t)}
        </Badge>
      </div>

      <div className="flex items-center gap-3">
        <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-chip-bg" aria-hidden="true">
          <div
            className={cn(
              "batch-bar-fill h-full rounded-full",
              expired ? "bg-border-strong" : "bg-[var(--plan-accent,var(--color-primary))]",
            )}
            style={{ width: `${String(Math.max(0, Math.min(100, percent)))}%` }}
          />
        </div>
        <p className="shrink-0 text-caption font-semibold text-text-secondary tabular-nums">
          {t("batch.remainingOfTotal", { remaining: batch.remainingAmount, total: batch.initialAmount })}
        </p>
      </div>
    </div>
  );
}


const LEDGER_TONE: Record<CreditLedgerType, "positive" | "negative" | "neutral"> = {
  GRANT: "positive",
  REFUND: "positive",
  CONSUME: "negative",
  EXPIRE: "negative",
  ADJUST: "neutral",
};

export function LedgerEntryRow({ entry, className }: { entry: LedgerEntry; className?: string }) {
  const { t } = useTranslation("credit");
  const tone = LEDGER_TONE[entry.type];
  const Icon = entry.amount >= 0 ? ArrowUp : ArrowDown;
  const { data: plans } = usePackageCatalog();
  const batchPlanName = entry.packageCode
    ? entry.packageCode === WELCOME_PACKAGE_CODE
      ? t("welcome.batchName")
      : (plans?.find((plan) => plan.code === entry.packageCode)?.name ?? entry.packageCode)
    : null;

  return (
    <div className={cn("flex items-center gap-3 px-3 py-3", className)}>
      <span
        className={cn(
          "flex size-9 shrink-0 items-center justify-center rounded-full",
          tone === "positive" ? "bg-success-bg" : tone === "negative" ? "bg-danger-bg" : "bg-chip-bg",
        )}
      >
        <Icon
          className={cn(
            "size-4",
            tone === "positive" ? "text-success-text" : tone === "negative" ? "text-danger-text" : "text-text-tertiary",
          )}
          aria-hidden="true"
        />
      </span>
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="truncate text-caption font-semibold text-text-primary">{t(`ledger.types.${entry.type}`)}</p>
        <p className="text-small text-text-tertiary">
          {new Date(entry.createdAt).toLocaleString("vi-VN", { dateStyle: "short", timeStyle: "short" })}
          {" · "}
          {t("ledger.balanceAfter", { count: entry.balanceAfter })}
        </p>
        {batchPlanName && entry.expiresAt ? (
          <p className="truncate text-small text-text-tertiary">
            {t("ledger.batchInfo", {
              name: batchPlanName,
              date: new Date(entry.expiresAt).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" }),
            })}
          </p>
        ) : null}
      </div>
      <p
        className={cn(
          "text-body font-bold tabular-nums",
          tone === "positive" ? "text-success-text" : tone === "negative" ? "text-danger-text" : "text-text-primary",
        )}
      >
        {entry.amount >= 0 ? `+${String(entry.amount)}` : entry.amount}
      </p>
    </div>
  );
}


export function EntitlementFeatureList({ entitlement, className }: { entitlement: Entitlement; className?: string }) {
  const { t } = useTranslation("credit");
  const features: { key: keyof Entitlement; label: string }[] = [
    { key: "hasHistory", label: t("entitlement.features.history") },
    { key: "hasTrend", label: t("entitlement.features.trend") },
    { key: "hasReminder", label: t("entitlement.features.reminder") },
    { key: "hasExport", label: t("entitlement.features.export") },
    { key: "storeImage", label: t("entitlement.features.storeImage") },
  ];

  return (
    <ul className={cn("flex flex-col gap-2", className)}>
      {features.map(({ key, label }) => {
        const enabled = Boolean(entitlement[key]);
        return (
          <li key={key} className="flex items-center gap-2">
            {enabled ? (
              <Check className="size-4 shrink-0 text-success-text" aria-hidden="true" />
            ) : (
              <X className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
            )}
            <span className={cn("text-body", enabled ? "text-text-primary" : "text-text-tertiary")}>{label}</span>
          </li>
        );
      })}
    </ul>
  );
}


export interface ActivationCodeFieldProps extends Omit<InputProps, "value" | "onChange"> {
  value: string;
  onChange: (value: string) => void;
}

export function ActivationCodeField({ value, onChange, ...props }: ActivationCodeFieldProps) {
  return (
    <Input
      value={value}
      onChange={(event) => {
        onChange(formatActivationCode(event.target.value));
      }}
      autoComplete="off"
      autoCapitalize="characters"
      spellCheck={false}
      inputMode="text"
      maxLength={24}
      {...props}
    />
  );
}
