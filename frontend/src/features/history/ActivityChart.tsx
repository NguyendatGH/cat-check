import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import { intlLocale } from "@/shared/lib/format/locale";
import type { ScanListItem } from "@/entities/scan-result";

const WEEKS = 18;
const LEVEL_CLASS = ["bg-border/40", "bg-primary/20", "bg-primary/45", "bg-primary/70", "bg-primary"] as const;

export function dayKey(date: Date): string {
  const y = String(date.getFullYear());
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

function level(count: number): number {
  if (count <= 0) return 0;
  return Math.min(count, 4);
}

interface Cell {
  key: string;
  date: Date;
  count: number;
  future: boolean;
}

export interface ActivityChartProps {
  items: ScanListItem[];
  selectedDay?: string | null;
  onSelectDay?: (day: string | null) => void;
  className?: string;
}

export function ActivityChart({ items, selectedDay, onSelectDay, className }: ActivityChartProps) {
  const { t } = useTranslation("history");
  const locale = intlLocale();

  const { columns, total, activeDays } = useMemo(() => {
    const counts = new Map<string, number>();
    items.forEach((item) => {
      const key = dayKey(new Date(item.capturedAt));
      counts.set(key, (counts.get(key) ?? 0) + 1);
    });

    const today = new Date();
    today.setHours(0, 0, 0, 0);
    const mondayOffset = (today.getDay() + 6) % 7;
    const start = new Date(today);
    start.setDate(today.getDate() - mondayOffset - (WEEKS - 1) * 7);

    const cols: Cell[][] = [];
    let sum = 0;
    let days = 0;
    for (let w = 0; w < WEEKS; w++) {
      const col: Cell[] = [];
      for (let d = 0; d < 7; d++) {
        const date = new Date(start);
        date.setDate(start.getDate() + w * 7 + d);
        const key = dayKey(date);
        const count = counts.get(key) ?? 0;
        const future = date > today;
        if (!future && count > 0) {
          sum += count;
          days += 1;
        }
        col.push({ key, date, count, future });
      }
      cols.push(col);
    }
    return { columns: cols, total: sum, activeDays: days };
  }, [items]);

  const monthFmt = new Intl.DateTimeFormat(locale, { month: "short" });
  const weekdayFmt = new Intl.DateTimeFormat(locale, { weekday: "narrow" });
  const fullFmt = new Intl.DateTimeFormat(locale, { day: "numeric", month: "numeric", year: "numeric" });

  return (
    <section className={cn("flex flex-col gap-3", className)} aria-label={t("activity.title")}>
      <div className="flex items-baseline justify-between gap-2">
        <p className="text-body font-bold text-text-primary">{t("activity.title")}</p>
        <p className="text-caption text-text-secondary">{t("activity.summary", { count: total, days: activeDays })}</p>
      </div>

      <div className="flex gap-1.5">
        <div className="grid shrink-0 grid-rows-[auto_repeat(7,1fr)] gap-[3px] pt-px text-[10px] leading-none text-text-tertiary">
          <span className="h-3" />
          {columns[0].map((cell, d) => (
            <span key={cell.key} className="flex items-center">
              {d % 2 === 0 ? weekdayFmt.format(cell.date) : ""}
            </span>
          ))}
        </div>

        <div className="grid min-w-0 flex-1 gap-[3px]" style={{ gridTemplateColumns: `repeat(${String(WEEKS)}, minmax(0, 1fr))` }}>
          {columns.map((col, w) => {
            const first = col[0].date;
            const showMonth = w === 0 || first.getMonth() !== columns[w - 1][0].date.getMonth();
            return (
              <div key={col[0].key} className="flex flex-col gap-[3px]">
                <span className="h-3 overflow-visible whitespace-nowrap text-[10px] leading-3 text-text-tertiary">
                  {showMonth && w < WEEKS - 1 ? monthFmt.format(first) : ""}
                </span>
                {col.map((cell) => {
                  if (cell.future) return <span key={cell.key} className="aspect-square w-full" />;
                  const selected = selectedDay === cell.key;
                  const label = t("activity.cellLabel", { date: fullFmt.format(cell.date), count: cell.count });
                  return (
                    <button
                      key={cell.key}
                      type="button"
                      title={label}
                      aria-label={label}
                      aria-pressed={selected}
                      disabled={cell.count === 0}
                      onClick={() => onSelectDay?.(selected ? null : cell.key)}
                      className={cn(
                        "aspect-square w-full rounded-[3px] transition-transform enabled:hover:scale-110 enabled:active:scale-95",
                        LEVEL_CLASS[level(cell.count)],
                        selected && "ring-2 ring-primary-dark ring-offset-1 ring-offset-surface",
                      )}
                    />
                  );
                })}
              </div>
            );
          })}
        </div>
      </div>

      <div className="flex items-center justify-end gap-1.5 text-[11px] text-text-tertiary">
        <span>{t("activity.less")}</span>
        {LEVEL_CLASS.map((cls) => (
          <span key={cls} className={cn("size-2.5 rounded-[2px]", cls)} />
        ))}
        <span>{t("activity.more")}</span>
      </div>
    </section>
  );
}
