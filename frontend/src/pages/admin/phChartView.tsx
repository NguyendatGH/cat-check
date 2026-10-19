import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip } from "antd";
import { AlertTriangle, ChevronRight, Info, ScanLine } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import {
  isHexColor,
  useAdminColorChart,
  type ChartStatus,
  type ColorChartPoint,
  type ColorChartSummary,
} from "@/features/admin";
import { formatPhShort, hasBatch, isUsedByScans, sortedPoints } from "./phChartModel";

const STATUS_CLASS: Record<ChartStatus, string> = {
  DRAFT: "bg-background-alt text-text-secondary ring-1 ring-border",
  ACTIVE: "bg-success-bg text-success-text",
  ARCHIVED: "bg-chip-bg text-text-tertiary",
};

const chipClass = "inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-small font-semibold";

export function ChartStatusBadge({ status }: { status: ChartStatus }) {
  const { t } = useTranslation("admin");
  return (
    <Tooltip title={t(`chart.statusHelp.${status}`)}>
      <span className={cn(chipClass, STATUS_CLASS[status])} title={t(`chart.statusHelp.${status}`)}>
        {t(`chart.status.${status}`)}
      </span>
    </Tooltip>
  );
}

export function PlaceholderBadge() {
  const { t } = useTranslation("admin");
  return (
    <Tooltip title={t("chart.placeholderHelp")}>
      <span className={cn(chipClass, "bg-warning-bg text-warning-text")} title={t("chart.placeholderHelp")}>
        <AlertTriangle size={13} aria-hidden="true" />
        {t("chart.placeholder")}
      </span>
    </Tooltip>
  );
}

export function ScanUseBadge() {
  const { t } = useTranslation("admin");
  return (
    <Tooltip title={t("chart.usedByScansHelp")}>
      <span className={cn(chipClass, "bg-info text-info-text")} title={t("chart.usedByScansHelp")}>
        <ScanLine size={13} aria-hidden="true" />
        {t("chart.usedByScans")}
      </span>
    </Tooltip>
  );
}

export function ChartBadges({ chart }: { chart: ColorChartSummary }) {
  return (
    <span className="flex flex-wrap items-center gap-1.5">
      <ChartStatusBadge status={chart.status} />
      {isUsedByScans(chart) ? <ScanUseBadge /> : null}
      {chart.placeholder ? <PlaceholderBadge /> : null}
    </span>
  );
}

export function useChartLabels() {
  const { t } = useTranslation("admin");
  return {
    productLine: (line: string | null) => (line === null ? t("chart.productLineNone") : t(`chart.productLine.${line}`, line)),
    batch: (chart: Pick<ColorChartSummary, "productionBatch">) =>
      hasBatch(chart) ? (chart.productionBatch ?? "") : t("chart.batchDefault"),
    source: (source: string) => t(`chart.source.${source}`, t("chart.sourceUnknown")),
  };
}

export function ChartMetaLine({ chart }: { chart: ColorChartSummary }) {
  const { t } = useTranslation("admin");
  const labels = useChartLabels();
  return (
    <p className="flex flex-wrap gap-x-3 gap-y-0.5 text-small text-text-secondary">
      <span>
        {t("chart.metaProductLine")}: <b className="font-semibold text-text-primary">{labels.productLine(chart.productLine)}</b>
      </span>
      <span>
        {t("chart.metaBatch")}: <b className="font-semibold text-text-primary">{labels.batch(chart)}</b>
      </span>
      <span>
        {t("chart.metaSource")}: <b className="font-semibold text-text-primary">{labels.source(chart.source)}</b>
      </span>
    </p>
  );
}

interface StripStop {
  key: string;
  phValue: number;
  hex: string;
  name?: string;
}

export function SwatchStrip({ stops, size = "md" }: { stops: readonly StripStop[]; size?: "sm" | "md" }) {
  const { t } = useTranslation("admin");
  if (stops.length === 0) {
    return <p className="text-small text-text-tertiary">{t("chart.noPoints")}</p>;
  }
  return (
    <ol className="flex flex-wrap gap-1" aria-label={t("chart.stripLabel")}>
      {stops.map((stop) => {
        const valid = isHexColor(stop.hex);
        const label = [t("chart.phValue", { value: formatPhShort(stop.phValue) }), stop.name, stop.hex]
          .filter((part) => part !== undefined && part !== "")
          .join(" · ");
        return (
          <li key={stop.key} className="flex flex-col items-center gap-0.5" title={label}>
            <span
              className={cn(
                "block rounded-md border",
                size === "sm" ? "h-5 w-7" : "h-8 w-10",
                valid ? "border-black/10" : "border-dashed border-border",
              )}
              style={valid ? { backgroundColor: stop.hex } : undefined}
              aria-hidden="true"
            />
            <span className="font-mono text-[11px] leading-none text-text-secondary">{formatPhShort(stop.phValue)}</span>
            <span className="sr-only">{label}</span>
          </li>
        );
      })}
    </ol>
  );
}

export function pointsToStops(points: readonly ColorChartPoint[]): StripStop[] {
  return sortedPoints(points).map((point) => ({
    key: point.id,
    phValue: point.phValue,
    hex: point.displayHex,
    name: point.displayNameVi,
  }));
}

/** Dải màu của một dòng trong danh sách; dữ liệu chi tiết được cache và dùng lại khi mở bảng. */
export function RowSwatchStrip({ chartId }: { chartId: string }) {
  const { t } = useTranslation("admin");
  const detail = useAdminColorChart(chartId);
  if (detail.isPending) {
    return <span className="block h-9 w-48 animate-pulse rounded-md bg-background-alt" aria-label={t("feedback.loading")} />;
  }
  if (detail.isError) {
    return <p className="text-small text-text-tertiary">{t("chart.stripError")}</p>;
  }
  return <SwatchStrip stops={pointsToStops(detail.data.data.points)} size="sm" />;
}

export function SwatchGrid({ points }: { points: readonly ColorChartPoint[] }) {
  const { t } = useTranslation("admin");
  const ordered = sortedPoints(points);
  if (ordered.length === 0) {
    return <p className="text-caption text-text-tertiary">{t("chart.noPoints")}</p>;
  }
  return (
    <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 lg:grid-cols-5">
      {ordered.map((point) => {
        const valid = isHexColor(point.displayHex);
        return (
          <li key={point.id} className="overflow-hidden rounded-xl border border-border bg-surface">
            <span
              className={cn("block h-14", !valid && "border-b border-dashed border-border")}
              style={valid ? { backgroundColor: point.displayHex } : undefined}
              aria-hidden="true"
            />
            <div className="flex flex-col gap-0.5 p-2">
              <span className="text-body font-bold text-text-primary">{t("chart.phValue", { value: formatPhShort(point.phValue) })}</span>
              <span className="truncate text-small text-text-secondary">{point.displayNameVi}</span>
              <span className="font-mono text-[11px] text-text-tertiary">{point.displayHex}</span>
            </div>
          </li>
        );
      })}
    </ul>
  );
}

const EXPLAINER_KEY = "catcheck.admin.phChart.explainerOpen";

function readExplainerOpen(): boolean {
  try {
    return window.localStorage.getItem(EXPLAINER_KEY) !== "0";
  } catch {
    return true;
  }
}

function LifecycleStep({ status, children }: { status: ChartStatus; children: ReactNode }) {
  return (
    <li className="flex min-w-0 flex-1 flex-col gap-1 rounded-xl bg-background-alt/70 p-3">
      <ChartStatusBadge status={status} />
      <span className="text-small text-text-secondary">{children}</span>
    </li>
  );
}

export function ChartExplainer() {
  const { t } = useTranslation("admin");
  const [open, setOpen] = useState(readExplainerOpen);
  return (
    <details
      open={open}
      onToggle={(event) => {
        const next = event.currentTarget.open;
        setOpen(next);
        try {
          window.localStorage.setItem(EXPLAINER_KEY, next ? "1" : "0");
        } catch {
          // Không lưu được thì lần sau mở mặc định.
        }
      }}
      className="group rounded-2xl border border-border bg-surface p-4"
    >
      <summary className="flex cursor-pointer list-none items-center gap-2 text-body font-semibold text-text-primary">
        <Info size={17} className="shrink-0 text-primary-dark" aria-hidden="true" />
        <span className="flex-1">{t("chart.explainer.title")}</span>
        <ChevronRight size={16} className="shrink-0 transition-transform group-open:rotate-90" aria-hidden="true" />
      </summary>
      <div className="flex flex-col gap-3 pt-3 text-caption text-text-secondary">
        <p>{t("chart.explainer.what")}</p>
        <p>{t("chart.explainer.scan")}</p>
        <ol className="flex flex-col gap-2 sm:flex-row" aria-label={t("chart.explainer.lifecycleLabel")}>
          <LifecycleStep status="DRAFT">{t("chart.explainer.draft")}</LifecycleStep>
          <LifecycleStep status="ACTIVE">{t("chart.explainer.active")}</LifecycleStep>
          <LifecycleStep status="ARCHIVED">{t("chart.explainer.archived")}</LifecycleStep>
        </ol>
        <p>{t("chart.explainer.edit")}</p>
      </div>
    </details>
  );
}
