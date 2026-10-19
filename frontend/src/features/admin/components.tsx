import { useId, type ReactNode, type SelectHTMLAttributes } from "react";
import { useTranslation } from "react-i18next";
import { Card as AntCard, Typography } from "antd";
import { PlugZap, ShieldAlert } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { isApiError } from "@/shared/api";
import { ADMIN_REASON_MIN_LENGTH } from "./api";

export interface AdminPageHeaderProps {
  title: string;
  description?: string;
  /** Thông tin phụ cho người vận hành (vd. ID đối tượng). Không dùng cho mã spec/endpoint (p17 A4). */
  meta?: string;
  actions?: ReactNode;
}

export function AdminPageHeader({ title, description, meta, actions }: AdminPageHeaderProps) {
  return (
    <header className="flex flex-wrap items-start justify-between gap-4" data-reveal>
      <div className="min-w-0">
        <Typography.Title level={1} className="!mb-0 !text-h2 !font-extrabold !text-text-primary">
          {title}
        </Typography.Title>
        {description !== undefined ? <p className="pt-1 text-body text-text-secondary">{description}</p> : null}
        {meta !== undefined ? <p className="pt-1 font-mono text-small text-text-tertiary">{meta}</p> : null}
      </div>
      {actions !== undefined ? <div className="flex shrink-0 flex-wrap gap-2">{actions}</div> : null}
    </header>
  );
}

export interface AdminSectionProps {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}

export function AdminSection({ title, description, actions, children, className }: AdminSectionProps) {
  const headingId = useId();
  return (
    <AntCard
      role="region"
      aria-labelledby={headingId}
      className={cn("shadow-brand-md", className)}
      title={
        <div className="py-1">
          <h2 id={headingId} className="text-h3 font-bold text-text-primary">
            {title}
          </h2>
          {description !== undefined ? (
            <p className="text-caption font-normal text-text-secondary">{description}</p>
          ) : null}
        </div>
      }
      extra={actions}
      styles={{ header: { paddingInline: 20 }, body: { padding: 20 } }}
    >
      <div className="flex flex-col gap-4">{children}</div>
    </AntCard>
  );
}

export function AdminTableScroll({ children }: { children: ReactNode }) {
  return <div className="-mx-1 overflow-x-auto px-1">{children}</div>;
}

export const adminTableClass = "w-full min-w-[720px] border-collapse text-caption";
export const adminThClass =
  "border-b border-border px-3 py-2 text-left text-overline font-semibold text-text-tertiary whitespace-nowrap";
export const adminTdClass = "border-b border-border/60 px-3 py-2 align-top text-text-primary";

export interface MissingApiEndpoint {
  code: string;
  signature: string;
}

export interface MissingApiNoticeProps {
  specSection: string;
  endpoints: MissingApiEndpoint[];
  note?: string;
}

export function MissingApiNotice({ specSection, endpoints, note }: MissingApiNoticeProps) {
  const { t } = useTranslation("admin");
  return (
    <section className="flex flex-col gap-4 rounded-2xl border border-dashed border-border bg-surface p-5">
      <div className="flex items-start gap-3">
        <span
          className="flex size-10 shrink-0 items-center justify-center rounded-full bg-background-alt text-text-tertiary"
          aria-hidden="true"
        >
          <PlugZap size={20} />
        </span>
        <div className="min-w-0">
          <h2 className="text-h3 font-bold text-text-primary">{t("noApi.title")}</h2>
          <p className="pt-0.5 text-caption text-text-secondary">{t("noApi.body")}</p>
        </div>
      </div>

      <div className="rounded-xl bg-background-alt/70 p-4">
        <p className="text-overline text-text-tertiary">{t("noApi.endpointsLabel")}</p>
        <ul className="flex flex-col gap-1.5 pt-2">
          {endpoints.map((endpoint) => (
            <li key={endpoint.code} className="flex flex-wrap items-baseline gap-2">
              <span className="rounded-md bg-chip-bg px-1.5 py-0.5 font-mono text-small font-bold text-text-secondary">
                {endpoint.code}
              </span>
              <code className="break-all font-mono text-small text-text-primary">{endpoint.signature}</code>
            </li>
          ))}
        </ul>
        <p className="pt-3 text-small text-text-tertiary">{t("noApi.specOwner", { section: specSection })}</p>
      </div>

      {note !== undefined ? <p className="text-caption text-text-secondary">{note}</p> : null}

      <p className="flex items-start gap-2 rounded-xl bg-warning-bg p-3 text-small text-warning-text">
        <ShieldAlert size={16} className="mt-px shrink-0" aria-hidden="true" />
        {t("noApi.capabilityRule")}
      </p>
    </section>
  );
}

export interface AdminSelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  children: ReactNode;
}

export function AdminSelect({ label, children, className, id, ...props }: AdminSelectProps) {
  const generatedId = useId();
  const selectId = id ?? generatedId;
  return (
    <div className="flex min-w-[160px] flex-col gap-1.5">
      <label htmlFor={selectId} className="text-caption font-semibold text-text-secondary">
        {label}
      </label>
      <select
        id={selectId}
        className={cn(
          "h-11 w-full rounded-xl bg-surface px-3 text-body text-text-primary shadow-xs",
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          "disabled:cursor-not-allowed disabled:opacity-50",
          className,
        )}
        {...props}
      >
        {children}
      </select>
    </div>
  );
}

export interface ReasonFieldProps {
  value: string;
  onChange: (value: string) => void;
  showError?: boolean;
  disabled?: boolean;
}

export function ReasonField({ value, onChange, showError = false, disabled = false }: ReasonFieldProps) {
  const { t } = useTranslation("admin");
  const fieldId = useId();
  const invalid = showError && value.trim().length < ADMIN_REASON_MIN_LENGTH;
  const describedBy = `${fieldId}-help`;
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={fieldId} className="text-caption font-semibold text-text-secondary">
        {t("reason.label")}
      </label>
      <textarea
        id={fieldId}
        value={value}
        disabled={disabled}
        rows={2}
        aria-invalid={invalid ? true : undefined}
        aria-describedby={describedBy}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        placeholder={t("reason.placeholder")}
        className={cn(
          "w-full resize-y rounded-xl bg-surface px-4 py-3 text-body text-text-primary shadow-xs",
          "placeholder:text-text-tertiary",
          "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
          "disabled:cursor-not-allowed disabled:opacity-50",
          invalid && "outline outline-2 outline-danger",
        )}
      />
      <p id={describedBy} className={cn("text-small", invalid ? "text-danger-text" : "text-text-tertiary")}>
        {invalid
          ? t("reason.tooShort", { min: ADMIN_REASON_MIN_LENGTH })
          : t("reason.help", { min: ADMIN_REASON_MIN_LENGTH })}
      </p>
    </div>
  );
}

export function ApiErrorNote({ error }: { error: unknown }) {
  const { t } = useTranslation("admin");
  if (error === null || error === undefined) {
    return null;
  }
  const code = isApiError(error) ? error.code : undefined;
  const message = error instanceof Error ? error.message : t("feedback.unknownError");
  return (
    <p role="alert" className="rounded-xl bg-danger-bg px-3 py-2 text-caption text-danger-text">
      {code !== undefined ? t("feedback.errorWithCode", { code, message }) : message}
    </p>
  );
}

export function SavedNote({ visible }: { visible: boolean }) {
  const { t } = useTranslation("admin");
  if (!visible) {
    return null;
  }
  return (
    <p aria-live="polite" className="text-caption text-success-text">
      {t("feedback.saved")}
    </p>
  );
}

export interface PagerProps {
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}

export function Pager({ page, totalPages, totalElements, onPageChange }: PagerProps) {
  const { t } = useTranslation("admin");
  const safeTotal = Math.max(totalPages, 1);
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 pt-1">
      <p className="text-caption text-text-secondary">
        {t("pager.summary", { page: page + 1, totalPages: safeTotal, totalElements })}
      </p>
      <div className="flex gap-2">
        <button
          type="button"
          disabled={page <= 0}
          onClick={() => {
            onPageChange(page - 1);
          }}
          className="rounded-lg border border-border px-3 py-1.5 text-caption font-semibold text-text-secondary disabled:opacity-40"
        >
          {t("pager.previous")}
        </button>
        <button
          type="button"
          disabled={page + 1 >= safeTotal}
          onClick={() => {
            onPageChange(page + 1);
          }}
          className="rounded-lg border border-border px-3 py-1.5 text-caption font-semibold text-text-secondary disabled:opacity-40"
        >
          {t("pager.next")}
        </button>
      </div>
    </div>
  );
}
