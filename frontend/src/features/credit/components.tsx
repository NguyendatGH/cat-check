import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import { ArrowDownCircle, ArrowUpCircle, Check, Clock, X } from "lucide-react";
import { Badge, Card, Input, type InputProps } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { formatActivationCode } from "./schemas";
import { usePackageCatalog } from "./hooks";
import type { CreditBatch, CreditLedgerType, Entitlement, LedgerEntry } from "./types";


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


export interface CreditBalanceSummaryProps {
  availableBalance: number;
  trialScansRemaining: number;
  nearestExpiringBatch?: CreditBatch;
  className?: string;
}

export function CreditBalanceSummary({
  availableBalance,
  trialScansRemaining,
  nearestExpiringBatch,
  className,
}: CreditBalanceSummaryProps) {
  const { t } = useTranslation("credit");
  return (
    <Card elevated className={cn("flex flex-col gap-4", className)}>
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-caption text-text-secondary">{t("balance.availableLabel")}</p>
          <p className="text-display font-bold text-text-primary">{availableBalance}</p>
          <p className="text-caption text-text-tertiary">{t("balance.unit")}</p>
        </div>
        {trialScansRemaining > 0 ? (
          <Badge tone="brand">{t("balance.trialRemaining", { count: trialScansRemaining })}</Badge>
        ) : null}
      </div>
      {nearestExpiringBatch ? (
        <div className="flex items-center gap-2 rounded-lg bg-background-alt p-3">
          <Clock className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
          <p className="text-caption text-text-secondary">
            {t("balance.nearestExpiring", {
              amount: nearestExpiringBatch.remainingAmount,
              remaining: formatRemainingSeconds(nearestExpiringBatch.remainingSeconds, t),
            })}
          </p>
        </div>
      ) : null}
    </Card>
  );
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
    <Card className={cn("flex flex-col gap-3", className)}>
      <div className="flex items-center justify-between gap-2">
        <p className="text-body font-semibold text-text-primary">
          {plans?.find((plan) => plan.code === batch.packageCode)?.name ?? batch.packageCode}
        </p>
        <Badge tone={expired ? "neutral" : "brand"}>
          {expired ? t("batch.expired") : formatRemainingSeconds(batch.remainingSeconds, t)}
        </Badge>
      </div>

      <div>
        <div className="h-2 w-full overflow-hidden rounded-full bg-chip-bg" role="img" aria-hidden="true">
          <div
            className="h-full rounded-full bg-primary"
            style={{ width: `${String(Math.max(0, Math.min(100, percent)))}%` }}
          />
        </div>
        <p className="mt-1.5 text-caption text-text-secondary">
          {t("batch.remainingOfTotal", { remaining: batch.remainingAmount, total: batch.initialAmount })}
        </p>
      </div>

      <p className="text-small text-text-tertiary">
        {t("batch.activatedAt", { date: new Date(batch.activatedAt).toLocaleDateString("vi-VN") })}
      </p>
    </Card>
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
  const Icon = entry.amount >= 0 ? ArrowUpCircle : ArrowDownCircle;
  const { data: plans } = usePackageCatalog();
  const batchPlanName = entry.packageCode
    ? (plans?.find((plan) => plan.code === entry.packageCode)?.name ?? entry.packageCode)
    : null;

  return (
    <div className={cn("flex items-center gap-3 rounded-lg border border-border bg-surface p-3", className)}>
      <Icon
        className={cn(
          "size-5 shrink-0",
          tone === "positive" ? "text-success-text" : tone === "negative" ? "text-danger-text" : "text-text-tertiary",
        )}
        aria-hidden="true"
      />
      <div className="flex min-w-0 flex-1 flex-col">
        <p className="truncate text-body font-medium text-text-primary">{t(`ledger.types.${entry.type}`)}</p>
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
          "text-body font-semibold",
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
