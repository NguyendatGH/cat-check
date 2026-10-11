import { useEffect, useId, useRef, type ReactNode, type TextareaHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { ArrowDown, ArrowUp, ArrowUpDown, RefreshCw, Search, ShieldOff, WifiOff, X } from "lucide-react";
import { Button, EmptyState, ErrorState, Input, SkeletonLoader } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import { classifyAdminError } from "./adminAccess";
import { ariaSortOf, type SortState } from "./tableTools";
import { shortId } from "./adminFormat";

/**
 * Bộ dựng "bảng dày" cho các trang quản trị: hàng KPI, bảng có header dính, sắp xếp, chọn
 * nhiều, thanh thao tác hàng loạt, và trạng thái tải/trống/lỗi. Chỉ ghép primitive có sẵn
 * của `shared/ui` và token màu của app — không thêm primitive mới.
 */

// ------------------------------------------------------------------- khung

export interface DensePanelProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

/** Khối nội dung mật độ cao: viền mảnh, header gọn, padding 12–16px. */
export function DensePanel({ title, description, actions, children, className }: DensePanelProps) {
  const headingId = useId();
  return (
    <section aria-labelledby={headingId} className={cn("rounded-xl border border-border bg-surface", className)}>
      <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border px-4 py-3">
        <div className="min-w-0">
          <h2 id={headingId} className="text-body font-bold text-text-primary">
            {title}
          </h2>
          {description !== undefined ? <p className="text-small text-text-secondary">{description}</p> : null}
        </div>
        {actions !== undefined ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
      </div>
      <div className="flex flex-col gap-3 p-3 sm:p-4">{children}</div>
    </section>
  );
}

// ------------------------------------------------------------------- KPI

export type KpiTone = "neutral" | "info" | "success" | "warning" | "danger";

const KPI_ACCENT: Record<KpiTone, string> = {
  neutral: "bg-border-strong",
  info: "bg-primary",
  success: "bg-success",
  warning: "bg-warning",
  danger: "bg-danger",
};

export interface KpiTileProps {
  label: string;
  value: string;
  hint?: string;
  tone?: KpiTone;
}

/** Ô số liệu: số dùng font mono + `tabular-nums` để các cột số thẳng hàng. */
export function KpiTile({ label, value, hint, tone = "neutral" }: KpiTileProps) {
  return (
    <div className="relative overflow-hidden rounded-xl border border-border bg-surface px-3 py-2.5">
      <span className={cn("absolute inset-y-0 left-0 w-1", KPI_ACCENT[tone])} aria-hidden="true" />
      <dt className="truncate text-small font-semibold text-text-secondary">{label}</dt>
      <dd className="pt-0.5 font-mono text-h3 font-bold tabular-nums text-text-primary">{value}</dd>
      {hint !== undefined ? <dd className="truncate text-small text-text-tertiary">{hint}</dd> : null}
    </div>
  );
}

/** Hàng KPI trên lưới 12 cột: 2 ô/hàng ở màn hẹp, 4 ô/hàng từ `lg`. */
export function KpiGrid({ children, label }: { children: ReactNode; label: string }) {
  return (
    <dl aria-label={label} className="grid grid-cols-2 gap-3 lg:grid-cols-12 lg:[&>*]:col-span-3">
      {children}
    </dl>
  );
}

export interface DistributionSegment {
  key: string;
  label: string;
  value: number;
  tone: KpiTone;
}

/**
 * Thanh phân bố có chú thích — biểu đồ tối giản cho số liệu phân loại. Không dùng recharts:
 * ESLint của dự án chỉ cho import recharts trong `features/trends` và `features/export`.
 * Chú thích luôn kèm con số, nên không phụ thuộc vào việc phân biệt màu.
 */
export function DistributionBar({ label, segments }: { label: string; segments: readonly DistributionSegment[] }) {
  const total = segments.reduce((sum, segment) => sum + segment.value, 0);
  const summary = segments.map((segment) => `${segment.label}: ${String(segment.value)}`).join(", ");
  return (
    <figure className="flex flex-col gap-2">
      <figcaption className="text-small font-semibold text-text-secondary">{label}</figcaption>
      <div
        role="img"
        aria-label={`${label} — ${summary}`}
        className="flex h-2.5 w-full overflow-hidden rounded-full bg-chip-bg"
      >
        {total > 0
          ? segments
              .filter((segment) => segment.value > 0)
              .map((segment) => (
                <span
                  key={segment.key}
                  className={KPI_ACCENT[segment.tone]}
                  style={{ width: `${String((segment.value / total) * 100)}%` }}
                />
              ))
          : null}
      </div>
      <ul className="flex flex-wrap gap-x-4 gap-y-1">
        {segments.map((segment) => (
          <li key={segment.key} className="flex items-center gap-1.5 text-small text-text-secondary">
            <span className={cn("size-2.5 rounded-sm", KPI_ACCENT[segment.tone])} aria-hidden="true" />
            {segment.label}
            <span className="font-mono font-semibold tabular-nums text-text-primary">{segment.value}</span>
          </li>
        ))}
      </ul>
    </figure>
  );
}

// ------------------------------------------------------------------- bảng

/** Khung cuộn của bảng: cuộn ngang ở màn hẹp, cuộn dọc để header `sticky` có tác dụng. */
export function DenseTableFrame({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div
      role="region"
      aria-label={label}
      // Vùng cuộn phải tới được bằng bàn phím để cuộn ngang/dọc bằng phím mũi tên (WCAG 2.1.1).
      // eslint-disable-next-line jsx-a11y/no-noninteractive-tabindex
      tabIndex={0}
      className="max-h-[65vh] overflow-auto rounded-lg border border-border"
    >
      {children}
    </div>
  );
}

export const denseTableClass = "w-full min-w-[760px] border-collapse text-caption";
export const denseThClass =
  "sticky top-0 z-[1] border-b border-border bg-background-alt px-3 py-2 text-left text-small font-semibold text-text-secondary whitespace-nowrap";
export const denseTdClass = "h-10 border-b border-border/60 px-3 py-1.5 align-middle text-text-primary";
export const denseRowClass = "hover:bg-background-alt/60 data-[selected=true]:bg-info/60";

export interface SortableThProps<K extends string> {
  label: string;
  sortKey: K;
  sort: SortState<K> | null;
  onSort: (key: K) => void;
  className?: string;
}

/** Tiêu đề cột bấm được để sắp xếp; `aria-sort` đặt trên `<th>` theo đúng ARIA APG. */
export function SortableTh<K extends string>({ label, sortKey, sort, onSort, className }: SortableThProps<K>) {
  const state = ariaSortOf(sort, sortKey);
  const Icon = state === "ascending" ? ArrowUp : state === "descending" ? ArrowDown : ArrowUpDown;
  return (
    <th scope="col" aria-sort={state} className={cn(denseThClass, className)}>
      <button
        type="button"
        onClick={() => {
          onSort(sortKey);
        }}
        className="-mx-1 inline-flex items-center gap-1 rounded px-1 hover:text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-[var(--focus-ring-color)]"
      >
        {label}
        <Icon size={13} aria-hidden="true" className={state === "none" ? "opacity-40" : undefined} />
      </button>
    </th>
  );
}

export interface CheckboxCellProps {
  checked: boolean | "mixed";
  onChange: () => void;
  label: string;
  disabled?: boolean;
}

/** Ô chọn dòng / chọn tất cả. `mixed` đặt `indeterminate` qua ref (HTML không có thuộc tính này). */
export function SelectCheckbox({ checked, onChange, label, disabled = false }: CheckboxCellProps) {
  const ref = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (ref.current !== null) {
      ref.current.indeterminate = checked === "mixed";
    }
  }, [checked]);
  return (
    <input
      ref={ref}
      type="checkbox"
      aria-label={label}
      checked={checked === true}
      disabled={disabled}
      onChange={onChange}
      className="size-4 cursor-pointer accent-primary-dark disabled:cursor-not-allowed"
    />
  );
}

export interface BulkActionBarProps {
  count: number;
  onClear: () => void;
  children: ReactNode;
}

/** Thanh thao tác hàng loạt, chỉ hiện khi có dòng được chọn. */
export function BulkActionBar({ count, onClear, children }: BulkActionBarProps) {
  const { t } = useTranslation("admin");
  if (count === 0) {
    return null;
  }
  return (
    <div
      role="region"
      aria-label={t("dense.bulkRegion")}
      className="flex flex-wrap items-center gap-2 rounded-lg border border-primary/30 bg-info px-3 py-2"
    >
      <p aria-live="polite" className="text-caption font-semibold text-info-text">
        {t("dense.selectedCount", { count })}
      </p>
      <div className="flex flex-1 flex-wrap items-center gap-2">{children}</div>
      <button
        type="button"
        onClick={onClear}
        className="inline-flex min-h-9 items-center gap-1 rounded-md px-2 text-caption font-semibold text-info-text hover:bg-surface/60"
      >
        <X size={14} aria-hidden="true" />
        {t("dense.clearSelection")}
      </button>
    </div>
  );
}

/** Khung xương bảng trong lúc tải — giữ chỗ đúng kích thước để trang không nhảy. */
export function TableSkeleton({ rows = 6 }: { rows?: number }) {
  const { t } = useTranslation("admin");
  return (
    <div role="status" aria-live="polite" className="flex flex-col gap-2 py-1">
      <span className="sr-only">{t("feedback.loading")}</span>
      {Array.from({ length: rows }, (_, index) => (
        <SkeletonLoader key={index} className="h-8 rounded-md" />
      ))}
    </div>
  );
}

export function KpiSkeleton() {
  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4" aria-hidden="true">
      {Array.from({ length: 4 }, (_, index) => (
        <SkeletonLoader key={index} className="h-[72px] rounded-xl" />
      ))}
    </div>
  );
}

// ------------------------------------------------------------------- ô nhập

export function SearchField({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
}) {
  return (
    <Input
      type="search"
      label={label}
      value={value}
      placeholder={placeholder}
      leftIcon={<Search size={16} />}
      className="h-10"
      onChange={(event) => {
        onChange(event.target.value);
      }}
    />
  );
}

export interface TextAreaFieldProps extends Omit<TextareaHTMLAttributes<HTMLTextAreaElement>, "onChange"> {
  label: string;
  value: string;
  onChange: (value: string) => void;
  error?: string;
  helperText?: string;
}

/** `<textarea>` có nhãn `htmlFor` và mô tả lỗi — `shared/ui` chưa có Textarea. */
export function TextAreaField({
  label,
  value,
  onChange,
  error,
  helperText,
  className,
  id,
  ...props
}: TextAreaFieldProps) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  const describedBy = `${fieldId}-desc`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fieldId} className="text-caption font-semibold text-text-secondary">
        {label}
      </label>
      <textarea
        id={fieldId}
        value={value}
        aria-invalid={error !== undefined ? true : undefined}
        aria-describedby={error !== undefined || helperText !== undefined ? describedBy : undefined}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        className={cn(
          "w-full resize-y rounded-xl border border-border-strong bg-surface px-3 py-2 text-body text-text-primary",
          "placeholder:text-text-tertiary",
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          error !== undefined && "border-2 border-danger",
          className,
        )}
        {...props}
      />
      {error !== undefined ? (
        <p id={describedBy} className="text-small text-danger-text">
          {error}
        </p>
      ) : helperText !== undefined ? (
        <p id={describedBy} className="text-small text-text-tertiary">
          {helperText}
        </p>
      ) : null}
    </div>
  );
}

// ------------------------------------------------------------------- hiển thị

/** UUID/hash rút gọn, giá trị đầy đủ ở `title` để rê chuột xem. */
export function MonoId({ value, chars = 8 }: { value: string | null | undefined; chars?: number }) {
  return (
    <code title={value ?? undefined} className="font-mono text-small text-text-secondary">
      {shortId(value, chars)}
    </code>
  );
}

/** Danh sách nhãn–giá trị dạng lưới cho khung chi tiết. */
export function DetailGrid({ items }: { items: readonly (readonly [string, ReactNode])[] }) {
  return (
    <dl className="grid grid-cols-1 gap-x-4 gap-y-2 sm:grid-cols-2">
      {items.map(([label, value]) => (
        <div key={label} className="min-w-0">
          <dt className="text-small text-text-tertiary">{label}</dt>
          <dd className="break-words text-caption text-text-primary">{value}</dd>
        </div>
      ))}
    </dl>
  );
}

// ------------------------------------------------------------------- trạng thái

/** Trang/khối mà người dùng không có vai trò để xem. */
export function AccessDeniedState({ description }: { description?: string }) {
  const { t } = useTranslation("admin");
  return (
    <EmptyState
      icon={<ShieldOff size={22} />}
      title={t("access.deniedTitle")}
      description={description ?? t("access.deniedBody")}
    />
  );
}

/**
 * Lỗi của một truy vấn: 401/403 → thông báo quyền (không có nút thử lại, thử lại cũng vô
 * ích); mất mạng → nút thử lại; 412 → nút tải lại; còn lại → thông điệp server + mã lỗi.
 */
export function AdminQueryError({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation("admin");
  const kind = classifyAdminError(error);
  if (kind === "unauthenticated" || kind === "forbidden") {
    return (
      <AccessDeniedState description={kind === "unauthenticated" ? t("access.sessionBody") : t("access.deniedBody")} />
    );
  }
  if (kind === "network") {
    return (
      <EmptyState
        icon={<WifiOff size={22} />}
        title={t("access.networkTitle")}
        description={t("access.networkBody")}
        action={
          onRetry !== undefined ? (
            <Button variant="tertiary" size="sm" onClick={onRetry}>
              <RefreshCw size={14} aria-hidden="true" />
              {t("access.retry")}
            </Button>
          ) : undefined
        }
      />
    );
  }
  const code = isApiError(error) ? error.code : undefined;
  const message = error instanceof Error ? error.message : t("feedback.unknownError");
  return (
    <ErrorState
      title={kind === "stale" ? t("access.staleTitle") : t("access.errorTitle")}
      description={code !== undefined ? t("feedback.errorWithCode", { code, message }) : message}
      onRetry={onRetry}
      retryLabel={kind === "stale" ? t("access.reload") : t("access.retry")}
    />
  );
}

/**
 * Lỗi của một thao tác ghi, hiện ngay dưới form. 412 có nút "Tải lại" để lấy ETag mới;
 * 401/403 nói rõ là thiếu quyền; mất mạng nói rõ là mất mạng.
 */
export function MutationErrorNote({ error, onReload }: { error: unknown; onReload?: () => void }) {
  const { t } = useTranslation("admin");
  if (error === null || error === undefined) {
    return null;
  }
  const kind = classifyAdminError(error);
  const code = isApiError(error) ? error.code : undefined;
  const serverMessage = error instanceof Error ? error.message : t("feedback.unknownError");
  let text: string;
  if (kind === "stale") text = t("access.staleBody");
  else if (kind === "unauthenticated") text = t("access.sessionBody");
  else if (kind === "forbidden") text = t("access.deniedBody");
  else if (kind === "network") text = t("access.networkBody");
  else text = code !== undefined ? t("feedback.errorWithCode", { code, message: serverMessage }) : serverMessage;
  return (
    <div
      role="alert"
      className={cn(
        "flex flex-wrap items-center justify-between gap-2 rounded-lg px-3 py-2 text-caption",
        kind === "stale" ? "bg-warning-bg text-warning-text" : "bg-danger-bg text-danger-text",
      )}
    >
      <p>{text}</p>
      {kind === "stale" && onReload !== undefined ? (
        <Button variant="tertiary" size="sm" onClick={onReload}>
          <RefreshCw size={14} aria-hidden="true" />
          {t("access.reload")}
        </Button>
      ) : null}
    </div>
  );
}
