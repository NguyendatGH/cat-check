import type { ReactNode } from "react";
import {
  BadgeCheck,
  CalendarDays,
  CheckCircle2,
  Clock,
  Download,
  FileWarning,
  Hourglass,
  IdCard,
  LineChart,
  Loader2,
  Palette,
  Utensils,
  type LucideIcon,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { Badge, buttonVariants, Card, Input } from "@/shared/ui";
import type { Cat } from "@/entities/cat";
import { CatCard } from "@/entities/cat";
import type { ExportJob, ExportRangePreset, ExportSection } from "./types";

/**
 * UI cho màn xuất hồ sơ (p9 §9.4.3, mockup `10. Xuất Hồ sơ Sức khỏe`). Ghép `entities/cat`
 * (`CatCard`) — feature → entity (bất kỳ entity) luôn được phép.
 *
 * Mockup `10` là MỘT TRANG CUỘN (ba mục cấu hình + xem trước + CTA hiện cùng lúc), không phải
 * wizard từng bước — các component ở đây được dựng để cả bản mobile lẫn desktop dùng chung.
 */

/* ---------------- ExportSectionHeading ---------------- */

export interface ExportSectionHeadingProps {
  /** Số thứ tự mục, VD "1." — bỏ trống cho mục không đánh số (VD "Xem trước báo cáo"). */
  index?: string;
  title: string;
  /** Chữ phụ bên phải (khoảng ngày đã chọn, "Hồ sơ đang theo dõi"…). */
  hint?: string;
  /** Hành động bên phải, VD nút "Chọn tất cả". */
  action?: ReactNode;
  className?: string;
}

export function ExportSectionHeading({ index, title, hint, action, className }: ExportSectionHeadingProps) {
  const trailing =
    action ?? (hint ? <span className="text-[11px] font-semibold text-primary-dark">{hint}</span> : null);
  return (
    // KHÔNG `flex-wrap`: mockup `10` luôn đặt chữ phụ/hành động CÙNG HÀNG với tiêu đề (tiêu đề
    // tự xuống dòng bên trong). Cho phép wrap thì "Chọn tất cả (4)" rơi xuống dòng riêng.
    <div className={cn("flex items-baseline justify-between gap-3", className)}>
      <h2 className="min-w-0 text-caption font-bold uppercase tracking-[0.4px] text-text-primary">
        {[index, title].filter(Boolean).join(" ")}
      </h2>
      {trailing ? <span className="shrink-0 text-right">{trailing}</span> : null}
    </div>
  );
}

/* ---------------- CatPickerStep ---------------- */

export interface CatPickerStepProps {
  cats: Cat[];
  selectedCatId: string | null;
  onSelect: (cat: Cat) => void;
  primaryLabel: string;
  secondaryLabel: string;
  /** Tuổi đã định dạng của từng bé (mockup `10`: "Luna 3 tuổi 2 tháng") — từ `ageMonths` của API. */
  ageLabelFor?: (cat: Cat) => string | undefined;
  className?: string;
}

/** Thẻ chọn bé (mockup `10`): ảnh · tên · giống/tuổi · badge "Mèo chính"/"Mèo phụ" theo `isPrimary`. */
export function CatPickerStep({
  cats,
  selectedCatId,
  onSelect,
  primaryLabel,
  secondaryLabel,
  ageLabelFor,
  className,
}: CatPickerStepProps) {
  return (
    <div className={cn("flex flex-col gap-3", className)}>
      {cats.map((cat) => (
        <CatCard
          key={cat.id}
          cat={cat}
          selected={cat.id === selectedCatId}
          onSelect={onSelect}
          ageLabel={ageLabelFor?.(cat)}
          roleLabel={cat.isPrimary ? primaryLabel : secondaryLabel}
        />
      ))}
    </div>
  );
}

/* ---------------- RangePickerStep ---------------- */

export interface RangePickerStepProps {
  value: ExportRangePreset;
  onChange: (preset: ExportRangePreset) => void;
  customFrom: string;
  customTo: string;
  onCustomChange: (from: string, to: string) => void;
  labels: Record<ExportRangePreset, string>;
  /** Preset được gợi ý — hiện thêm hậu tố `recommendedSuffix` (mockup `10`: "30 ngày qua"). */
  recommended?: ExportRangePreset;
  recommendedSuffix?: string;
  fromLabel: string;
  toLabel: string;
  className?: string;
}

const RANGE_OPTIONS: ExportRangePreset[] = ["7D", "30D", "90D", "CUSTOM"];

/** Lưới 2×2 các khoảng thời gian (mockup `10`), ô đang chọn nền `primary-dark`. */
export function RangePickerStep({
  value,
  onChange,
  customFrom,
  customTo,
  onCustomChange,
  labels,
  recommended,
  recommendedSuffix,
  fromLabel,
  toLabel,
  className,
}: RangePickerStepProps) {
  return (
    <div className={cn("flex flex-col gap-4", className)}>
      <div className="grid grid-cols-2 gap-2">
        {RANGE_OPTIONS.map((option) => {
          const selected = value === option;
          return (
            <button
              key={option}
              type="button"
              onClick={() => {
                onChange(option);
              }}
              aria-pressed={selected}
              className={cn(
                "flex min-h-[var(--touch-target-min)] items-center justify-center gap-1.5 rounded-xl border px-3 py-2 text-center text-caption font-semibold transition-colors",
                selected
                  ? "border-primary-dark bg-primary-dark text-white shadow-sm"
                  : "border-border bg-surface text-text-secondary hover:border-border-strong",
              )}
            >
              {option === "CUSTOM" ? <CalendarDays className="size-4 shrink-0" aria-hidden="true" /> : null}
              <span>
                {labels[option]}
                {recommended === option && recommendedSuffix ? ` ${recommendedSuffix}` : ""}
              </span>
            </button>
          );
        })}
      </div>
      {value === "CUSTOM" ? (
        <div className="flex gap-3">
          <Input
            type="date"
            label={fromLabel}
            value={customFrom}
            onChange={(e) => {
              onCustomChange(e.target.value, customTo);
            }}
            className="flex-1"
          />
          <Input
            type="date"
            label={toLabel}
            value={customTo}
            onChange={(e) => {
              onCustomChange(customFrom, e.target.value);
            }}
            className="flex-1"
          />
        </div>
      ) : null}
    </div>
  );
}

/* ---------------- SectionsChecklist ---------------- */

export interface SectionItem {
  value: ExportSection;
  title: string;
  description: string;
}

export interface SectionsChecklistProps {
  items: SectionItem[];
  selected: ExportSection[];
  onToggle: (section: ExportSection) => void;
  className?: string;
}

/** Icon đầu dòng cho từng mục nội dung — khớp mockup `10` (biểu đồ / màu / thức ăn / hồ sơ). */
const SECTION_ICON: Record<ExportSection, LucideIcon> = {
  TREND: LineChart,
  SCAN_LOG: Palette,
  NOTES: Utensils,
  PROFILE: IdCard,
};

export function SectionsChecklist({ items, selected, onToggle, className }: SectionsChecklistProps) {
  return (
    <ul className={cn("flex flex-col divide-y divide-border", className)}>
      {items.map((item) => {
        const checked = selected.includes(item.value);
        const Icon = SECTION_ICON[item.value];
        return (
          <li key={item.value}>
            <label className="flex min-h-[var(--touch-target-min)] cursor-pointer items-start gap-3 py-3">
              <span
                aria-hidden="true"
                className={cn(
                  "flex size-8 shrink-0 items-center justify-center rounded-lg",
                  checked ? "bg-chip-bg text-primary-dark" : "bg-background-alt text-text-tertiary",
                )}
              >
                <Icon className="size-4" />
              </span>
              <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                <span className="text-caption font-bold text-text-primary">{item.title}</span>
                <span className="text-small leading-snug text-text-secondary">{item.description}</span>
              </span>
              <input
                type="checkbox"
                aria-label={item.title}
                checked={checked}
                onChange={() => {
                  onToggle(item.value);
                }}
                className="mt-0.5 size-5 shrink-0 rounded accent-[var(--color-primary-dark)]"
              />
            </label>
          </li>
        );
      })}
    </ul>
  );
}

/* ---------------- ReportPreviewThumbnail ---------------- */

export interface ReportPreviewThumbnailProps {
  watermark: string;
  className?: string;
}

/**
 * Ảnh thu nhỏ trang A4 — DỰNG BẰNG CSS, không phải ảnh render thật từ backend: J1 chỉ trả
 * `pageCount`/`scanCount` SAU khi job chạy xong, trước khi bấm tạo thì chưa có gì để render.
 */
export function ReportPreviewThumbnail({ watermark, className }: ReportPreviewThumbnailProps) {
  return (
    <div
      className={cn("mx-auto flex h-[196px] w-[146px] flex-col gap-2 rounded-lg bg-surface p-3 shadow-sm", className)}
    >
      <div className="flex items-center justify-between">
        <span className="h-1.5 w-12 rounded-full bg-primary-dark" />
        <span className="h-1.5 w-5 rounded-full bg-border" />
      </div>
      <span className="h-1 w-full rounded-full bg-border" />
      <span className="h-1 w-3/4 rounded-full bg-border" />
      <div className="flex h-14 items-end justify-center gap-1.5 pt-1">
        <span className="h-7 w-2.5 rounded-sm bg-primary-dark" />
        <span className="h-11 w-2.5 rounded-sm bg-primary" />
        <span className="h-5 w-2.5 rounded-sm bg-primary-dark" />
        <span className="h-9 w-2.5 rounded-sm bg-secondary" />
        <span className="h-6 w-2.5 rounded-sm bg-success" />
      </div>
      <span className="h-1 w-full rounded-full bg-border" />
      <span className="h-1 w-5/6 rounded-full bg-border" />
      <span className="h-1 w-2/3 rounded-full bg-border" />
      <div className="mt-auto flex items-center gap-1.5 border-t border-border pt-2">
        <BadgeCheck className="size-3.5 shrink-0 text-primary" aria-hidden="true" />
        <span className="truncate text-[8px] font-bold tracking-[0.4px] text-text-secondary">{watermark}</span>
      </div>
    </div>
  );
}

/* ---------------- ExportJobStatusCard ---------------- */

const STATUS_META: Record<ExportJob["status"], { icon: typeof Clock; tone: string }> = {
  QUEUED: { icon: Hourglass, tone: "text-text-tertiary" },
  RUNNING: { icon: Loader2, tone: "text-primary" },
  READY: { icon: CheckCircle2, tone: "text-ph-normal-text" },
  FAILED: { icon: FileWarning, tone: "text-danger-text" },
  EXPIRED: { icon: Clock, tone: "text-text-tertiary" },
};

export interface ExportJobStatusCardProps {
  job: ExportJob;
  statusLabel: string;
  documentCodeLabel: string;
  /** "12 trang · 38 lượt quét" — chỉ khi `READY` và backend đã trả `pageCount`/`scanCount`. */
  pageCountLabel?: string;
  /** "Còn 6 ngày 04 giờ để tải" — tính từ `expiresAt` của job. */
  expiryLabel?: string;
  /** Câu giải thích dưới tiêu đề cho `FAILED`/`EXPIRED`. */
  hint?: string;
  /** Nhãn a11y của thanh tiến trình (QUEUED/RUNNING). */
  progressLabel: string;
  downloadLabel: string;
  downloadHref: string;
  /** Nút tạo lại (`FAILED` → "Thử lại", `EXPIRED` → "Tạo lại"). */
  retryLabel?: string;
  onRetry?: () => void;
  className?: string;
}

/**
 * Thẻ trạng thái job xuất PDF — frame `M-10a` / `Web - 10a` (design/figma-plugin `30-core.js`,
 * `x1_exportCard`): icon · tiêu đề trạng thái · mã hồ sơ · thanh tiến trình (đang chạy) ·
 * số trang/lượt quét + hạn tải + nút tải (sẵn sàng) · lời nhắn + nút tạo lại (lỗi/hết hạn).
 *
 * `failureReason` của backend KHÔNG được in ra: chuỗi đó viết tiếng Việt không dấu ("Co loi
 * khi sinh bao cao…") — dùng câu i18n tương đương thay thế.
 */
export function ExportJobStatusCard({
  job,
  statusLabel,
  documentCodeLabel,
  pageCountLabel,
  expiryLabel,
  hint,
  progressLabel,
  downloadLabel,
  downloadHref,
  retryLabel,
  onRetry,
  className,
}: ExportJobStatusCardProps) {
  const meta = STATUS_META[job.status];
  const Icon = meta.icon;
  const inProgress = job.status === "QUEUED" || job.status === "RUNNING";
  return (
    <Card padding="lg" className={cn("flex flex-col items-center gap-4 text-center", className)}>
      <Icon className={cn("size-12", meta.tone, job.status === "RUNNING" && "animate-spin")} aria-hidden="true" />
      <div className="flex flex-col items-center gap-2">
        <p className="text-h3 font-bold text-text-primary">{statusLabel}</p>
        {job.documentCode ? (
          <Badge tone="neutral">
            {documentCodeLabel}: {job.documentCode}
          </Badge>
        ) : null}
      </div>

      {inProgress ? (
        // Backend không trả phần trăm — thanh chạy vô định (indeterminate), không bịa số %.
        <div
          role="progressbar"
          aria-label={progressLabel}
          aria-busy="true"
          className="h-2 w-full overflow-hidden rounded-full bg-chip-bg"
        >
          <div className="h-full w-2/5 animate-pulse rounded-full bg-primary" />
        </div>
      ) : null}

      {pageCountLabel ? <p className="text-caption text-text-secondary">{pageCountLabel}</p> : null}
      {expiryLabel ? <p className="-mt-2 text-caption text-text-tertiary">{expiryLabel}</p> : null}
      {hint ? (
        <p className={cn("text-caption", job.status === "FAILED" ? "text-danger-text" : "text-text-secondary")}>
          {hint}
        </p>
      ) : null}

      {job.status === "READY" ? (
        <a href={downloadHref} download className={cn(buttonVariants({ variant: "primary" }), "w-full gap-2")}>
          <Download className="size-4" aria-hidden="true" />
          {downloadLabel}
        </a>
      ) : null}
      {retryLabel && onRetry ? (
        <button type="button" onClick={onRetry} className={cn(buttonVariants({ variant: "primary" }), "w-full")}>
          {retryLabel}
        </button>
      ) : null}
    </Card>
  );
}
