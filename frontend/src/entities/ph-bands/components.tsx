import {
  AlertTriangle,
  ArrowDownCircle,
  ArrowUpCircle,
  CheckCircle,
  HelpCircle,
  Info,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import { phTokenStyle } from "./colorToken";
import type { PhBand } from "./model";


const ICON_MAP: Record<string, LucideIcon> = {
  "check-circle": CheckCircle,
  "arrow-down-circle": ArrowDownCircle,
  "arrow-up-circle": ArrowUpCircle,
  "alert-triangle": AlertTriangle,
  "help-circle": HelpCircle,
};

function resolveIcon(iconName: string): LucideIcon {
  return ICON_MAP[iconName] ?? Info;
}

export interface PhBadgeProps {
  band: PhBand;
  className?: string;
}

export function PhBadge({ band, className }: PhBadgeProps) {
  const style = phTokenStyle(band.colorToken);
  const Icon = resolveIcon(band.iconName);
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-caption font-semibold",
        style.bg,
        style.text,
        className,
      )}
    >
      <Icon className="size-4 shrink-0" aria-hidden="true" />
      {band.label}
    </span>
  );
}

const OPEN_END_PAD_RATIO = 0.3;

export interface PhGaugeBarProps {
  bands: PhBand[];
  value?: number | null;
  domain?: { min: number; max: number };
  className?: string;
}

export function PhGaugeBar({ bands, value, domain, className }: PhGaugeBarProps) {
  const isNumber = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);
  const axisBands = bands
    .filter((b) => isNumber(b.phMin) || isNumber(b.phMax))
    .sort((a, b) => a.sortOrder - b.sortOrder);
  const bounds = [...axisBands.map((b) => b.phMin), ...axisBands.map((b) => b.phMax)].filter(isNumber);
  const innerMin = bounds.length ? Math.min(...bounds) : 0;
  const innerMax = bounds.length ? Math.max(...bounds) : 14;
  const pad = (innerMax - innerMin || 1) * OPEN_END_PAD_RATIO;
  const hasOpenLow = axisBands.some((b) => !isNumber(b.phMin));
  const hasOpenHigh = axisBands.some((b) => !isNumber(b.phMax));
  const min = domain?.min ?? (hasOpenLow ? innerMin - pad : innerMin);
  const max = domain?.max ?? (hasOpenHigh ? innerMax + pad : innerMax);
  const span = max - min || 1;

  const clampPercent = (ph: number) => Math.min(100, Math.max(0, ((ph - min) / span) * 100));

  return (
    <div className={cn("flex flex-col gap-1", className)}>
      <div className="relative h-3 w-full overflow-hidden rounded-full" role="img" aria-hidden="true">
        <div className="flex h-full w-full">
          {axisBands.map((band) => {
            const segMin = band.phMin ?? min;
            const segMax = band.phMax ?? max;
            const widthPercent = Math.max(0, clampPercent(segMax) - clampPercent(segMin));
            if (widthPercent <= 0) return null;
            return (
              <span
                key={band.code}
                style={{ width: `${String(widthPercent)}%` }}
                className={cn("shrink-0", phTokenStyle(band.colorToken).solid)}
              />
            );
          })}
        </div>
        {value !== null && value !== undefined ? (
          <span
            className="absolute top-1/2 size-3.5 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 border-surface bg-text-primary shadow-sm"
            style={{ left: `${String(clampPercent(value))}%` }}
          />
        ) : null}
      </div>
      <div className="flex justify-between text-small text-text-tertiary">
        <span>{min.toFixed(1)}</span>
        <span>{max.toFixed(1)}</span>
      </div>
    </div>
  );
}

export interface PhRangeLegendProps {
  bands: PhBand[];
  className?: string;
}

export function PhRangeLegend({ bands, className }: PhRangeLegendProps) {
  return (
    <ul className={cn("flex flex-wrap gap-x-4 gap-y-1.5", className)}>
      {bands
        .slice()
        .sort((a, b) => a.sortOrder - b.sortOrder)
        .map((band) => (
          <li key={band.code} className="flex items-center gap-1.5 text-caption text-text-secondary">
            <span
              aria-hidden="true"
              className={cn("size-2.5 shrink-0 rounded-full", phTokenStyle(band.colorToken).solid)}
            />
            {band.label}
          </li>
        ))}
    </ul>
  );
}

export function PhDisclaimerNote({ className }: { className?: string }) {
  const { t } = useTranslation("scan");
  return (
    <p className={cn("text-small text-text-tertiary", className)} role="note">
      {t("phBands.disclaimer.short")}
    </p>
  );
}
