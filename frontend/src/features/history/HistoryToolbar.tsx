import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { CalendarDays, Search, X } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { dayKey } from "./ActivityChart";

export type HistoryRangeKey = "3" | "7" | "30" | "all";
export const HISTORY_RANGES: readonly HistoryRangeKey[] = ["3", "7", "30", "all"];

export interface HistoryToolbarProps {
  range: HistoryRangeKey;
  onRangeChange: (range: HistoryRangeKey) => void;
  search: string;
  onSearchChange: (value: string) => void;
  selectedDay: string | null;
  onSelectDay: (day: string | null) => void;
  className?: string;
}

export function HistoryToolbar({
  range,
  onRangeChange,
  search,
  onSearchChange,
  selectedDay,
  onSelectDay,
  className,
}: HistoryToolbarProps) {
  const { t, i18n } = useTranslation("history");
  const [searchOpen, setSearchOpen] = useState(search.length > 0);
  const dateRef = useRef<HTMLInputElement>(null);
  const showSearch = searchOpen || search.length > 0;

  const openDatePicker = () => {
    const input = dateRef.current;
    if (!input) return;
    try {
      input.showPicker();
    } catch {
      input.focus();
    }
  };

  const dayLabel = selectedDay
    ? new Date(`${selectedDay}T00:00:00`).toLocaleDateString(i18n.language || "vi", { day: "numeric", month: "numeric" })
    : null;

  return (
    <div className={cn("flex flex-col gap-2", className)}>
      <div className="flex items-center gap-2">
        <div
          role="radiogroup"
          aria-label={t("toolbar.rangeLabel")}
          className={cn(
            "flex min-w-0 flex-1 gap-1 rounded-xl bg-background-alt p-1 transition-opacity",
            selectedDay && "opacity-50",
          )}
        >
          {HISTORY_RANGES.map((key) => {
            const active = !selectedDay && range === key;
            return (
              <button
                key={key}
                type="button"
                role="radio"
                aria-checked={active}
                onClick={() => {
                  onSelectDay(null);
                  onRangeChange(key);
                }}
                className={cn(
                  "min-h-9 flex-1 whitespace-nowrap rounded-lg px-2 text-small font-semibold transition-colors",
                  active ? "bg-surface text-primary-dark shadow-xs" : "text-text-secondary hover:text-text-primary",
                )}
              >
                {t(`toolbar.range.${key}`)}
              </button>
            );
          })}
        </div>

        <button
          type="button"
          aria-label={t("toolbar.search")}
          aria-pressed={showSearch}
          onClick={() => {
            if (showSearch) onSearchChange("");
            setSearchOpen(!showSearch);
          }}
          className={cn(
            "flex size-11 shrink-0 items-center justify-center rounded-xl border transition-colors",
            showSearch ? "border-primary bg-chip-bg text-primary-dark" : "border-border bg-surface text-text-secondary",
          )}
        >
          <Search className="size-4" aria-hidden="true" />
        </button>

        <div className="relative shrink-0">
          {selectedDay ? (
            <button
              type="button"
              onClick={() => {
                onSelectDay(null);
              }}
              aria-label={t("toolbar.clearDate")}
              className="flex h-11 items-center gap-1.5 rounded-xl border border-primary bg-chip-bg px-3 text-small font-semibold text-primary-dark"
            >
              <CalendarDays className="size-4" aria-hidden="true" />
              {dayLabel}
              <X className="size-3.5" aria-hidden="true" />
            </button>
          ) : (
            <button
              type="button"
              aria-label={t("toolbar.pickDate")}
              onClick={openDatePicker}
              className="flex size-11 items-center justify-center rounded-xl border border-border bg-surface text-text-secondary transition-colors hover:text-text-primary"
            >
              <CalendarDays className="size-4" aria-hidden="true" />
            </button>
          )}
          <input
            ref={dateRef}
            type="date"
            tabIndex={-1}
            aria-hidden="true"
            max={dayKey(new Date())}
            value={selectedDay ?? ""}
            onChange={(e) => {
              onSelectDay(e.target.value || null);
            }}
            className="pointer-events-none absolute bottom-0 right-0 size-px opacity-0"
          />
        </div>
      </div>

      {showSearch ? (
        <div className="content-enter relative">
          <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-text-tertiary" aria-hidden="true" />
          <input
            type="search"
            // eslint-disable-next-line jsx-a11y/no-autofocus
            autoFocus
            value={search}
            onChange={(e) => {
              onSearchChange(e.target.value);
            }}
            placeholder={t("toolbar.searchPlaceholder")}
            aria-label={t("toolbar.search")}
            className="h-11 w-full rounded-xl border border-border bg-surface pl-9 pr-10 text-small outline-none placeholder:text-text-tertiary focus:border-primary focus:ring-2 focus:ring-primary/20"
          />
          {search ? (
            <button
              type="button"
              onClick={() => {
                onSearchChange("");
              }}
              aria-label={t("toolbar.clearSearch")}
              className="absolute right-1 top-1/2 flex size-9 -translate-y-1/2 items-center justify-center text-text-tertiary hover:text-text-primary"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
