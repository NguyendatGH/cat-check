import { format, eachDayOfInterval, getDay, getWeek } from "date-fns";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import type { ScanListItem } from "@/entities/scan-result";

interface ActivityData {
  date: Date;
  count: number;
  week: number;
  day: number;
}

function generateActivityData(items: ScanListItem[]): Map<string, number> {
  const activityMap = new Map<string, number>();
  items.forEach((item) => {
    const dateStr = format(new Date(item.capturedAt), "yyyy-MM-dd");
    activityMap.set(dateStr, (activityMap.get(dateStr) ?? 0) + 1);
  });
  return activityMap;
}

function getIntensityClass(count: number): string {
  if (count === 0) return "bg-border/30";
  if (count === 1 || count === 2) return "bg-green-100 dark:bg-green-900/30";
  if (count >= 3) return "bg-green-600 dark:bg-green-700";
  return "bg-border/30";
}

export interface ActivityChartProps {
  items: ScanListItem[];
  onDateClick?: (date: Date) => void;
  selectedDate?: Date | null;
  className?: string;
}

export function ActivityChart({ items, onDateClick, selectedDate, className }: ActivityChartProps) {
  const { t } = useTranslation("history");
  const activityMap = generateActivityData(items);

  // Generate last 12 weeks of data
  const today = new Date();
  const startDate = new Date(today);
  startDate.setDate(startDate.getDate() - 84); // 12 weeks back
  const endDate = today;

  const days = eachDayOfInterval({ start: startDate, end: endDate });

  // Group by week
  const weeks: Map<number, ActivityData[]> = new Map();
  days.forEach((date) => {
    const week = getWeek(date, { weekStartsOn: 1 });
    const day = getDay(date);
    const dateStr = format(date, "yyyy-MM-dd");
    const count = activityMap.get(dateStr) ?? 0;

    const weekList = weeks.get(week) ?? [];
    weekList.push({
      date,
      count,
      week,
      day,
    });
    weeks.set(week, weekList);
  });

  const sortedWeeks = Array.from(weeks.entries())
    .sort(([a], [b]) => a - b)
    .map(([, days]) => days);

  const dayLabels = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

  return (
    <div className={cn("flex flex-col gap-3", className)}>
      <div>
        <p className="text-small font-semibold text-text-secondary">{t("activity.title")}</p>
      </div>

      <div className="overflow-x-auto">
        <div className="inline-block min-w-full">
          {/* Day labels */}
          <div className="flex gap-0.5">
            <div className="w-10 shrink-0" /> {/* Spacer for week numbers */}
            {dayLabels.map((label) => (
              <div key={label} className="size-6 text-center text-caption font-medium text-text-tertiary">
                {label.charAt(0)}
              </div>
            ))}
          </div>

          {/* Heatmap grid */}
          <div className="flex flex-col gap-0.5">
            {sortedWeeks.map((weekDays, weekIndex) => (
              <div key={weekIndex} className="flex gap-0.5">
                <div className="w-10 shrink-0 text-caption text-text-tertiary">
                  {format(weekDays[0].date, "MMM d")}
                </div>
                {dayLabels.map((_, dayIndex) => {
                  const dayData = weekDays.find((d) => d.day === dayIndex);
                  if (!dayData) {
                    return <div key={dayIndex} className="size-6 rounded-sm bg-border/20" />;
                  }

                  const isSelected = selectedDate && format(dayData.date, "yyyy-MM-dd") === format(selectedDate, "yyyy-MM-dd");

                  return (
                    <button
                      key={dayIndex}
                      type="button"
                      onClick={() => onDateClick?.(dayData.date)}
                      className={cn(
                        "size-6 rounded-sm border transition-all hover:ring-2 hover:ring-primary/30",
                        isSelected ? "ring-2 ring-primary border-primary" : "border-border/30",
                        getIntensityClass(dayData.count),
                      )}
                      style={{
                        opacity: dayData.count === 0 ? 0.5 : 1,
                      }}
                      title={`${format(dayData.date, "MMM d, yyyy")}: ${dayData.count.toString()} scan${dayData.count !== 1 ? "s" : ""}`}
                      aria-label={`${format(dayData.date, "MMM d, yyyy")}: ${dayData.count.toString()} scan${dayData.count !== 1 ? "s" : ""}`}
                    />
                  );
                })}
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Legend */}
      <div className="flex flex-wrap items-center gap-3 text-caption text-text-secondary">
        <span>{t("activity.less")}</span>
        <div className="flex gap-1">
          <div className="size-3 rounded-sm bg-border/30" />
          <div className="size-3 rounded-sm bg-green-100 dark:bg-green-900/30" />
          <div className="size-3 rounded-sm bg-green-600 dark:bg-green-700" />
        </div>
        <span>{t("activity.more")}</span>
      </div>
    </div>
  );
}
