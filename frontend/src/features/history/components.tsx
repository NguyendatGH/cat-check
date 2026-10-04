import type { ReactNode } from "react";
import { format, isToday, isYesterday } from "date-fns";
import {
  AlertTriangle,
  CalendarDays,
  CheckCircle,
  ChevronDown,
  ChevronRight,
  FileText,
  Flag,
  ListFilter,
  FileDown,
  ShieldCheck,
  type LucideIcon,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import { Card } from "@/shared/ui";
import type { PhBand } from "@/entities/ph-bands";
import { phTokenStyle } from "@/entities/ph-bands";
import type { ScanListItem } from "@/entities/scan-result";
import type { HistoryFilterKey } from "./types";

/**
 * UI cho `features/history` — ghép `entities/ph-bands` (màu/nhãn) + `entities/scan-result`
 * (dữ liệu quét). Không đặt ở entity vì 1 entity không được import entity khác.
 *
 * Bố cục bám mockup `07. Lịch sử Sức khỏe (Health History)`: thanh chuyển bé ở đầu màn,
 * phổ pH kèm chú giải dải, chip lọc có icon, thẻ timeline có badge dải + chip phụ.
 * Mọi ngưỡng/nhãn dải đến từ `bands` (`GET /reference/ph-bands`) — không hard-code.
 */

/** Nhãn ngày kiểu "Hôm nay, 08:42" / "Hôm qua, 19:15" / "20/09/2026, 08:42" — số hoá, không phụ
 * thuộc locale month-name của date-fns (tránh lệch mock "Th09" không chuẩn). */
export function formatScanTimestamp(iso: string, todayLabel: string, yesterdayLabel: string): string {
  const date = new Date(iso);
  const time = format(date, "HH:mm");
  if (isToday(date)) return `${todayLabel}, ${time}`;
  if (isYesterday(date)) return `${yesterdayLabel}, ${time}`;
  return `${format(date, "dd/MM/yyyy")}, ${time}`;
}

export function monthGroupKey(iso: string): string {
  return format(new Date(iso), "yyyy-MM");
}

/** `{month, year}` số (không zero-pad) để truyền thẳng vào `t("monthGroup.label", {month, year})`
 * (i18next tự nội suy `{{month}}`/`{{year}}` — không tự ráp chuỗi ở tầng component). */
export function parseMonthGroupKey(key: string): { month: number; year: number } {
  const [year, month] = key.split("-");
  return { month: Number(month), year: Number(year) };
}

const isBound = (v: number | null | undefined): v is number => typeof v === "number" && Number.isFinite(v);

/** Dải nào có ít nhất một đầu mút — bỏ dải "không kết luận" (không nằm trên thang). */
function scaleBands(bands: PhBand[]): PhBand[] {
  return bands
    .filter((b) => isBound(b.phMin) || isBound(b.phMax))
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);
}

/* ---------------- CatSwitcherBar ---------------- */

export interface CatSwitcherBarProps {
  name: string;
  avatar: ReactNode;
  /** VD "18 tháng tuổi • Cái" — đã dựng sẵn ở trang cha. */
  meta?: string | null;
  /** Dòng trạng thái pH, lấy từ dải của lần quét/trung vị gần nhất. */
  status: string;
  statusTone: "normal" | "muted";
  changeLabel: string;
  ariaLabel: string;
  onChange: () => void;
  className?: string;
}

/**
 * Thanh "đang xem bé nào + đổi bé" ở đầu màn lịch sử (mockup `07`). Không tự gọi API —
 * trang cha truyền tên/ảnh/trạng thái xuống.
 */
export function CatSwitcherBar({
  name,
  avatar,
  meta,
  status,
  statusTone,
  changeLabel,
  ariaLabel,
  onChange,
  className,
}: CatSwitcherBarProps) {
  return (
    <div
      aria-label={ariaLabel}
      className={cn("flex items-center gap-3 rounded-xl border border-border bg-surface p-3", className)}
    >
      {avatar}
      <div className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className="flex flex-wrap items-center gap-2">
          <span className="truncate text-body font-bold text-text-primary">{name}</span>
          {meta ? (
            <span className="shrink-0 rounded-full bg-chip-bg px-2 py-0.5 text-small text-text-secondary">{meta}</span>
          ) : null}
        </span>
        <span
          className={cn(
            "flex items-center gap-1 text-small",
            statusTone === "normal" ? "text-ph-normal-text" : "text-text-tertiary",
          )}
        >
          <ShieldCheck className="size-3.5 shrink-0" aria-hidden="true" />
          <span className="truncate">{status}</span>
        </span>
      </div>
      <button
        type="button"
        onClick={onChange}
        className="flex min-h-[var(--touch-target-min)] shrink-0 items-center gap-1 rounded-full bg-chip-bg px-3 text-caption font-semibold text-primary-dark"
      >
        {changeLabel}
        <ChevronDown className="size-4" aria-hidden="true" />
      </button>
    </div>
  );
}

/* ---------------- PhBandLegendRow ---------------- */

export interface PhBandLegendRowProps {
  bands: PhBand[];
  className?: string;
}

/**
 * Ba mốc chú giải dưới thanh phổ pH ("toan • tối ưu • kiềm" của mockup) — dựng từ chính
 * `bands` của API: dải đầu thang, dải `NORMAL`, dải cuối thang. Không hard-code ngưỡng.
 */
export function PhBandLegendRow({ bands, className }: PhBandLegendRowProps) {
  const { t } = useTranslation("history");
  const scale = scaleBands(bands);
  if (scale.length === 0) return null;

  const first = scale[0];
  const last = scale[scale.length - 1];
  const middle = scale.find((b) => b.severity === "NORMAL") ?? scale[Math.floor(scale.length / 2)];
  const picked = [first, middle, last].filter((b, i, arr) => arr.findIndex((x) => x.code === b.code) === i);

  const labelFor = (band: PhBand): string => {
    if (isBound(band.phMin) && isBound(band.phMax)) {
      return t("spectrum.bandRange", { label: band.label, min: band.phMin.toFixed(1), max: band.phMax.toFixed(1) });
    }
    if (isBound(band.phMax)) return t("spectrum.bandBelow", { label: band.label, max: band.phMax.toFixed(1) });
    if (isBound(band.phMin)) return t("spectrum.bandAbove", { label: band.label, min: band.phMin.toFixed(1) });
    return band.label;
  };

  return (
    <ul className={cn("flex flex-wrap items-center justify-between gap-x-3 gap-y-1", className)}>
      {picked.map((band) => (
        <li
          key={band.code}
          className={cn(
            "text-small",
            band.severity === "NORMAL" ? "font-semibold text-ph-normal-text" : "text-text-tertiary",
          )}
        >
          {labelFor(band)}
        </li>
      ))}
    </ul>
  );
}

/* ---------------- FilterChips ---------------- */

const FILTER_ICON: Record<HistoryFilterKey, LucideIcon> = {
  ALL: ListFilter,
  ABNORMAL: AlertTriangle,
  DISPUTED: Flag,
};

export interface FilterChipsProps {
  value: HistoryFilterKey;
  onChange: (value: HistoryFilterKey) => void;
  labels: Record<HistoryFilterKey, string>;
  className?: string;
}

export function FilterChips({ value, onChange, labels, className }: FilterChipsProps) {
  const options: HistoryFilterKey[] = ["ALL", "ABNORMAL", "DISPUTED"];
  return (
    <div className={cn("flex gap-2 overflow-x-auto pb-1", className)} role="tablist">
      {options.map((option) => {
        const Icon = FILTER_ICON[option];
        return (
          <button
            key={option}
            type="button"
            role="tab"
            aria-selected={value === option}
            onClick={() => {
              onChange(option);
            }}
            className={cn(
              "flex min-h-[var(--touch-target-min)] shrink-0 items-center gap-1.5 rounded-full border-2 px-4 text-caption font-semibold transition-colors",
              value === option
                ? "border-primary bg-primary text-white"
                : "border-border bg-surface text-text-secondary hover:border-border-strong",
            )}
          >
            <Icon className="size-4 shrink-0" aria-hidden="true" />
            {labels[option]}
          </button>
        );
      })}
    </div>
  );
}

/* ---------------- MonthGroupHeader ---------------- */

export function MonthGroupHeader({ label, count }: { label: string; count: string }) {
  return (
    <div className="flex items-center justify-between gap-2 px-1 pt-2">
      <p className="flex items-center gap-1.5 text-body font-bold text-text-primary">
        <CalendarDays className="size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
        {label}
      </p>
      <p className="shrink-0 text-small text-text-tertiary">{count}</p>
    </div>
  );
}

/* ---------------- ScanTimelineItem ---------------- */

export interface ScanTimelineItemProps {
  scan: ScanListItem;
  bands: PhBand[];
  timestampLabel: string;
  disputedLabel: string;
  detailLabel: string;
  onOpen: (scanId: string) => void;
  className?: string;
}

/**
 * Thẻ một lần quét trong timeline (mockup `07`): icon trạng thái · ngày giờ · pill
 * "{dải} • pH {giá trị}" · hàng chip phụ (tên bé, độ tin cậy, gần ngưỡng, tranh chấp).
 *
 * KHÔNG có đoạn mô tả văn xuôi như mockup — `GET /scans` không trả trường mô tả nào và
 * không bịa nội dung quan sát.
 */
export function ScanTimelineItem({
  scan,
  bands,
  timestampLabel,
  disputedLabel,
  detailLabel,
  onOpen,
  className,
}: ScanTimelineItemProps) {
  const { t } = useTranslation("history");
  const band = bands.find((b) => b.code === scan.bandCode);
  const style = phTokenStyle(band?.colorToken ?? "color-ph-unknown");
  const isAbnormal = scan.classification !== "IN_RANGE";
  const phLabel =
    scan.phValue != null
      ? t("item.phSummary", { label: band?.label ?? scan.classification, value: scan.phValue.toFixed(1) })
      : t("item.phUnknown", { label: band?.label ?? scan.classification });

  const chips: string[] = [];
  if (scan.catName) chips.push(scan.catName);
  if (scan.confidence !== null) chips.push(t("item.confidence", { value: Math.round(scan.confidence * 100) }));
  if (scan.nearBoundary) chips.push(t("item.nearBoundary"));
  if (scan.disputed) chips.push(disputedLabel);
  if (scan.hasNote) chips.push(t("item.hasNote"));

  return (
    <button
      type="button"
      onClick={() => {
        onOpen(scan.scanId);
      }}
      className={cn(
        "flex w-full items-start gap-3 rounded-xl border border-border bg-surface p-3 text-left transition-colors hover:border-border-strong",
        className,
      )}
    >
      <span
        className={cn("mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full", style.bg)}
        aria-hidden="true"
      >
        {isAbnormal ? (
          <AlertTriangle className={cn("size-4", style.text)} />
        ) : (
          <CheckCircle className={cn("size-4", style.text)} />
        )}
      </span>

      <span className="flex min-w-0 flex-1 flex-col gap-1.5">
        <span className="flex items-start justify-between gap-2">
          <span className="text-body font-bold text-text-primary">{timestampLabel}</span>
          <ChevronRight className="mt-0.5 size-4 shrink-0 text-text-tertiary" aria-hidden="true" />
        </span>

        <span className={cn("w-fit rounded-full px-2.5 py-1 text-caption font-semibold", style.bg, style.text)}>
          {phLabel}
        </span>

        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          {chips.map((chip) => (
            <span key={chip} className="rounded-full bg-background-alt px-2 py-0.5 text-small text-text-secondary">
              {chip}
            </span>
          ))}
          <span className="ml-auto text-caption font-semibold text-primary">{detailLabel}</span>
        </span>
      </span>
    </button>
  );
}

/* ---------------- HistoryTipCard ---------------- */

export interface HistoryTipCardProps {
  title: string;
  body: string;
  imageSrc: string;
  className?: string;
}

/** Thẻ mẹo tần suất quét — nội dung tĩnh theo mockup `07`, không có endpoint. */
export function HistoryTipCard({ title, body, imageSrc, className }: HistoryTipCardProps) {
  return (
    <div className={cn("flex gap-3 rounded-xl bg-background-alt p-3", className)}>
      <img src={imageSrc} alt="" className="size-14 shrink-0 rounded-xl object-cover" />
      <div className="min-w-0">
        <p className="text-caption font-bold text-primary-dark">{title}</p>
        <p className="pt-1 text-small leading-relaxed text-text-secondary">{body}</p>
      </div>
    </div>
  );
}

/* ---------------- ExportCtaCard ---------------- */

export interface ExportCtaCardProps {
  title: string;
  description: string;
  ctaLabel: string;
  onExport: () => void;
  className?: string;
}

export function ExportCtaCard({ title, description, ctaLabel, onExport, className }: ExportCtaCardProps) {
  return (
    <Card padding="md" className={cn("flex flex-col gap-3", className)}>
      <div className="flex items-start gap-3">
        <span
          className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-secondary-light"
          aria-hidden="true"
        >
          <FileText className="size-5 text-secondary-text-on" />
        </span>
        <div className="flex flex-col gap-1">
          <p className="text-body font-bold text-text-primary">{title}</p>
          <p className="text-caption text-text-secondary">{description}</p>
        </div>
      </div>
      <button
        type="button"
        onClick={onExport}
        className="flex min-h-[var(--touch-target-min)] w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 text-caption font-bold text-white"
      >
        <FileDown className="size-4 shrink-0" aria-hidden="true" />
        {ctaLabel}
      </button>
    </Card>
  );
}
