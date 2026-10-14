import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Check, Clock, Mars, Venus } from "lucide-react";
import type { Cat } from "@/entities/cat";
import { CatAvatar } from "@/entities/cat";
import type { PhBand } from "@/entities/ph-bands";
import { phTokenStyle } from "@/entities/ph-bands";
import type { ScanListItem } from "@/entities/scan-result";
import { cn } from "@/shared/lib/cn";

const isNum = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);

function useRelativeScanTime() {
  const { t, i18n } = useTranslation("scan");
  return (iso: string) => {
    const date = new Date(iso);
    const locale = i18n.language || "vi";
    const time = new Intl.DateTimeFormat(locale, { hour: "2-digit", minute: "2-digit" }).format(date);
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);
    const dayMs = 24 * 60 * 60 * 1000;
    if (date.getTime() >= startOfToday.getTime()) return t("selectCat.lastScanToday", { time });
    if (date.getTime() >= startOfToday.getTime() - dayMs) return t("selectCat.lastScanYesterday", { time });
    const day = new Intl.DateTimeFormat(locale, { day: "2-digit", month: "2-digit" }).format(date);
    return t("selectCat.lastScanAt", { date: `${day} ${time}` });
  };
}

function CheckMark({ selected }: { selected: boolean }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "flex size-6 shrink-0 items-center justify-center rounded-full border-2 transition-[background-color,border-color,transform] duration-200",
        selected ? "scale-100 border-primary-dark bg-primary-dark text-white" : "scale-90 border-border bg-surface text-transparent",
      )}
    >
      <Check className="size-3.5" strokeWidth={3} />
    </span>
  );
}

interface OptionShellProps {
  selected: boolean;
  onSelect: () => void;
  leading: ReactNode;
  children: ReactNode;
}

function OptionShell({ selected, onSelect, leading, children }: OptionShellProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      data-reveal-item
      className={cn(
        "flex w-full items-center gap-3 rounded-2xl border px-3 py-3 text-left transition-[background-color,border-color,box-shadow] duration-200",
        selected
          ? "border-primary-dark bg-chip-bg shadow-xs"
          : "border-border/60 bg-surface hover:border-border",
      )}
    >
      {leading}
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">{children}</span>
      <CheckMark selected={selected} />
    </button>
  );
}

export interface CatChoiceProps {
  cat: Cat;
  bands: PhBand[];
  latestScan?: ScanListItem | null;
  selected: boolean;
  onSelect: () => void;
}

export function CatChoice({ cat, bands, latestScan, selected, onSelect }: CatChoiceProps) {
  const { t } = useTranslation("scan");
  const relative = useRelativeScanTime();

  const sexLabel =
    cat.sex === "FEMALE" ? t("selectCat.sexFemale") : cat.sex === "MALE" ? t("selectCat.sexMale") : t("selectCat.sexUnknown");
  const SexIcon = cat.sex === "MALE" ? Mars : Venus;
  const ageLabel = !isNum(cat.ageMonths)
    ? null
    : cat.ageMonths >= 12
      ? t("selectCat.ageYears", { count: Math.floor(cat.ageMonths / 12) })
      : t("selectCat.ageMonthsShort", { count: cat.ageMonths });
  const meta = [sexLabel, ageLabel, isNum(cat.weightKg) ? t("selectCat.weight", { value: cat.weightKg }) : null]
    .filter((part): part is string => Boolean(part))
    .join(" · ");

  const lastScanAt = latestScan?.capturedAt ?? cat.lastScanAt ?? null;
  const lastBand = bands.find((band) => band.code === (latestScan?.bandCode ?? cat.lastClassification));
  const lastPh = latestScan && isNum(latestScan.phValue) ? latestScan.phValue : null;

  return (
    <OptionShell
      selected={selected}
      onSelect={onSelect}
      leading={<CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-11 shrink-0" />}
    >
      <span className="truncate text-body font-bold text-text-primary">{cat.name}</span>
      <span className="flex min-w-0 items-center gap-1 text-small text-text-secondary">
        <SexIcon className="size-3 shrink-0" aria-hidden="true" />
        <span className="truncate">{meta}</span>
      </span>
      {lastBand || lastScanAt || latestScan === null ? (
        <span className="flex min-w-0 items-center gap-2 pt-0.5 text-small text-text-tertiary">
          {lastBand ? (
            <span className="inline-flex shrink-0 items-center gap-1.5 font-semibold text-text-secondary">
              <span className={cn("size-2 rounded-full", phTokenStyle(lastBand.colorToken).solid)} aria-hidden="true" />
              {lastPh !== null ? t("selectCat.lastPh", { value: lastPh.toFixed(1) }) : lastBand.label}
            </span>
          ) : null}
          {lastScanAt ? (
            <span className="inline-flex min-w-0 items-center gap-1">
              <Clock className="size-3 shrink-0" aria-hidden="true" />
              <span className="truncate">{relative(lastScanAt)}</span>
            </span>
          ) : latestScan === null ? (
            <span className="truncate">{t("selectCat.noScanYet")}</span>
          ) : null}
        </span>
      ) : null}
    </OptionShell>
  );
}

export interface SharedChoiceProps {
  selected: boolean;
  onSelect: () => void;
  icon: ReactNode;
}

export function SharedChoice({ selected, onSelect, icon }: SharedChoiceProps) {
  const { t } = useTranslation("scan");
  return (
    <OptionShell
      selected={selected}
      onSelect={onSelect}
      leading={
        <span className="flex size-11 shrink-0 items-center justify-center rounded-full bg-secondary/25 text-secondary-text-on">
          {icon}
        </span>
      }
    >
      <span className="truncate text-body font-bold text-text-primary">{t("selectCat.sharedOptionTitle")}</span>
      <span className="line-clamp-2 text-small leading-snug text-text-secondary">{t("selectCat.sharedOptionDescription")}</span>
    </OptionShell>
  );
}
