import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { CalendarClock, Gauge } from "lucide-react";
import type { ScanListItem } from "@/entities/scan-result";
import type { PhBand } from "@/entities/ph-bands";
import { PhBadge } from "@/entities/ph-bands";
import { PhGaugeCircular } from "@/features/scan";
import { formatScanTimestamp } from "@/features/history";
import { cn } from "@/shared/lib/cn";

export interface DashboardPhCardProps {
  scan: ScanListItem;
  band: PhBand | undefined;
  bands: PhBand[];
  catName?: string | null;
  footer?: ReactNode;
  className?: string;
}

export function DashboardPhCard({ scan, band, bands, catName, footer, className }: DashboardPhCardProps) {
  const { t } = useTranslation("common");
  const stamp = formatScanTimestamp(scan.capturedAt, t("dashboard.today"), t("dashboard.yesterday"));

  return (
    <div className={cn("overflow-hidden rounded-2xl border border-border bg-surface shadow-brand-sm", className)}>
      <div className="flex items-center justify-between gap-3 px-4 pt-3.5 sm:px-5 sm:pt-4">
        <p className="min-w-0 truncate text-[13px] font-semibold text-text-primary">
          {catName ? t("dashboard.sessionOf", { name: catName }) : t("dashboard.latestMonitoring")}
        </p>
        <span className="flex shrink-0 items-center gap-1 text-[12px] text-text-tertiary">
          <CalendarClock className="size-3.5" aria-hidden="true" />
          {stamp}
        </span>
      </div>

      <div className="flex items-center gap-3 px-3 pb-3 pt-1 sm:gap-5 sm:px-5 sm:pb-4">
        <PhGaugeCircular
          bands={bands}
          value={scan.phValue}
          activeBand={band}
          markerHex={scan.thumbnailHex}
          variant="dial"
          size="xs"
          className="shrink-0"
        />

        <div className="flex min-w-0 flex-1 flex-col gap-2">
          {band ? <PhBadge band={band} className="self-start text-[11px] leading-4 [&>svg]:size-3.5" /> : null}
          {band?.description ? (
            <p className="line-clamp-3 text-[12px] leading-[18px] text-text-secondary">{band.description}</p>
          ) : null}
          {scan.confidenceBand ? (
            <span className="flex items-center gap-1.5 text-[12px] text-text-tertiary">
              <Gauge className="size-3.5 shrink-0" aria-hidden="true" />
              <span className="truncate">
                {t("dashboard.confidence", { value: t(`dashboardWeb.confidence${scan.confidenceBand}`) })}
              </span>
            </span>
          ) : null}
        </div>
      </div>

      {footer ? (
        <div className="flex items-center justify-end border-t border-border/70 bg-background-alt/40 px-4 py-2.5 sm:px-5">
          {footer}
        </div>
      ) : null}
    </div>
  );
}
