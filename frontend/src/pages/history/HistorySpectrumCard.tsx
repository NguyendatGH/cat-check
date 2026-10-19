import { useTranslation } from "react-i18next";
import { PhBadge, findBandForPh, phTokenStyle, type PhBand } from "@/entities/ph-bands";
import { PhGaugeCircular } from "@/features/scan";
import { cn } from "@/shared/lib/cn";

export interface HistorySpectrumCardProps {
  bands: PhBand[];
  median: number | null;
  scanCount: number;
  className?: string;
}

export function HistorySpectrumCard({ bands, median, scanCount, className }: HistorySpectrumCardProps) {
  const { t } = useTranslation("history");
  const band = median != null ? findBandForPh(bands, median) : null;
  const sorted = [...bands].sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <section className={cn("flex items-center gap-3 rounded-2xl bg-surface p-3 shadow-xs sm:gap-4 sm:p-4", className)}>
      <PhGaugeCircular bands={bands} value={median} activeBand={band ?? undefined} variant="dial" size="xs" />
      <div className="flex min-w-0 flex-1 flex-col gap-2">
        <div>
          <p className="text-small font-semibold text-text-secondary">{t("spectrum.title")}</p>
          <p className="text-caption text-text-tertiary">{t("spectrum.basedOn", { count: scanCount })}</p>
        </div>
        {band ? <PhBadge band={band} className="self-start text-[11px] leading-4 [&>svg]:size-3.5" /> : null}
        <ul className="flex flex-wrap gap-x-3 gap-y-1">
          {sorted.map((b) => (
            <li key={b.code} className="flex items-center gap-1.5 text-[11px] text-text-secondary">
              <span className={cn("size-2 rounded-full", phTokenStyle(b.colorToken).solid)} aria-hidden="true" />
              <span className={cn(band?.code === b.code && "font-semibold text-text-primary")}>{b.label}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  );
}
