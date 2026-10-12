import { useId, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { z } from "zod";
import {
  AlertTriangle,
  BadgeCheck,
  Bell,
  CalendarDays,
  Check,
  CircleCheck,
  Clock,
  Lock,
  Mail,
  Repeat,
  Smartphone,
  Sun,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import {
  INTERVAL_DAYS_MAX,
  INTERVAL_DAYS_MIN,
  REMINDER_CHANNELS,
  apiTimeToInput,
  inputTimeToApi,
  type PatchReminderPayload,
  type Reminder,
  type ReminderChannel,
} from "@/features/reminder";
import vetFollowupPhoto from "@/shared/assets/images/web-reminder/vet-followup.jpg";



const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

export const PRESET_INTERVALS = [1, 3, 7] as const;

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function useNextRunLabel(): (reminder: Pick<Reminder, "type">) => string {
  const { t } = useTranslation("reminder");
  return (reminder) => (reminder.type === "SCAN_ROUTINE" ? t("list.nextRunLabel") : t("list.nextReminderLabel"));
}

export function reminderAction(reminder: Pick<Reminder, "type" | "id">): { to: string; labelKey: string } {
  if (reminder.type === "SCAN_ROUTINE") return { to: "/scan", labelKey: "web.scanNowCta" };
  if (reminder.type === "CREDIT_EXPIRY") return { to: "/credits", labelKey: "web.viewCreditsCta" };
  return { to: `/reminders/${reminder.id}`, labelKey: "list.openDetail" };
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" });
}

export type DueState =
  | { kind: "none" }
  | { kind: "overdue"; days: number }
  | { kind: "hours"; hours: number }
  | { kind: "days"; days: number };

export function dueState(nextRunAt: string | undefined, now: number = Date.now()): DueState {
  if (nextRunAt === undefined) return { kind: "none" };
  const delta = new Date(nextRunAt).getTime() - now;
  if (Number.isNaN(delta)) return { kind: "none" };
  if (delta < 0) return { kind: "overdue", days: Math.floor(-delta / DAY_MS) };
  if (delta < DAY_MS) return { kind: "hours", hours: Math.max(1, Math.round(delta / HOUR_MS)) };
  return { kind: "days", days: Math.round(delta / DAY_MS) };
}

export function useScheduleLabel(): (reminder: Reminder) => string {
  const { t } = useTranslation("reminder");
  return (reminder) => {
    if (reminder.scheduleKind === "RRULE") return t("schedule.rrule");
    const days = reminder.intervalDays;
    if (days === undefined) return t("schedule.unknown");
    if (days === 1) return t("schedule.intervalDaily");
    if (days === 7) return t("schedule.intervalWeekly");
    return t("schedule.intervalDays", { count: days });
  };
}

export function timeWindow(reminder: Reminder): { from: string; to: string } | null {
  const { preferredTimeStart, preferredTimeEnd } = reminder;
  if (preferredTimeStart === undefined || preferredTimeEnd === undefined) return null;
  return { from: preferredTimeStart.slice(0, 5), to: preferredTimeEnd.slice(0, 5) };
}


export function StatusChip({ reminder, due }: { reminder: Reminder; due: DueState }) {
  const { t } = useTranslation("reminder");
  if (!reminder.active) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-background-alt px-2.5 py-1 text-small font-semibold text-text-secondary">
        {t("status.paused")}
      </span>
    );
  }
  if (due.kind === "overdue") {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-danger-bg px-2.5 py-1 text-small font-semibold text-danger-text">
        <AlertTriangle size={12} aria-hidden="true" />
        {t("status.overdue")}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-success-bg px-2.5 py-1 text-small font-semibold text-success-text">
      <Check size={12} aria-hidden="true" />
      {t("status.active")}
    </span>
  );
}

export function DueChip({ due }: { due: DueState }) {
  const { t } = useTranslation("reminder");
  if (due.kind === "none") return null;
  if (due.kind === "overdue") {
    return (
      <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-danger-bg px-3 py-1 text-small font-bold text-danger-text">
        <AlertTriangle size={12} aria-hidden="true" />
        {due.days === 0 ? t("list.overdueToday") : t("list.overdueDays", { days: due.days })}
      </span>
    );
  }
  return (
    <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-secondary px-3 py-1 text-small font-bold text-secondary-text-on">
      <Clock size={12} aria-hidden="true" />
      {due.kind === "hours" ? t("list.dueInHours", { hours: due.hours }) : t("list.dueInDays", { days: due.days })}
    </span>
  );
}

export function BreedChip({ label }: { label: string }) {
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-deco-backdrop px-2.5 py-0.5 text-small font-semibold text-text-secondary">
      {label}
    </span>
  );
}

export function NeedScanChip() {
  const { t } = useTranslation("reminder");
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-danger-bg px-2.5 py-0.5 text-small font-bold text-danger-text">
      {t("list.needScan")}
    </span>
  );
}

export function ReminderStatusLine({
  reminder,
  due,
  schedule,
}: {
  reminder: Reminder;
  due: DueState;
  schedule: string;
}) {
  const { t } = useTranslation("reminder");
  if (!reminder.active) {
    return <p className="pt-0.5 text-caption text-text-secondary">{t("list.scheduleLinePaused", { schedule })}</p>;
  }
  const overdue = due.kind === "overdue";
  return (
    <p
      className={cn(
        "flex items-center gap-1.5 pt-0.5 text-small font-bold",
        overdue ? "text-danger-text" : "text-success-text",
      )}
    >
      {overdue ? (
        <AlertTriangle size={14} className="shrink-0" aria-hidden="true" />
      ) : (
        <CircleCheck size={14} className="shrink-0" aria-hidden="true" />
      )}
      {t("list.statusLine", {
        status: overdue ? t("status.overdue") : t("status.active"),
        schedule,
      })}
    </p>
  );
}


export function FeatureLockedCard() {
  const { t } = useTranslation(["reminder", "common"]);
  return (
    <div className="flex flex-col items-start gap-3 rounded-2xl bg-surface p-5 shadow-brand-md">
      <span className="inline-flex items-center gap-1.5 rounded-full bg-chip-bg px-3 py-1 text-caption font-semibold text-primary-dark">
        <Lock size={13} aria-hidden="true" />
        {t("upsell.title", { ns: "common" })}
      </span>
      <p className="text-body text-text-secondary">{t("upsell.description", { ns: "common" })}</p>
      <p className="text-caption text-text-secondary">{t("errors.lockedNote")}</p>
      <Link
        to="/credits"
        className="inline-flex items-center justify-center rounded-xl bg-primary-dark px-4 py-2.5 text-caption font-bold text-white shadow-sm"
      >
        {t("errors.lockedCta")}
      </Link>
    </div>
  );
}

export function LimitReachedNotice({ existingReminderId }: { existingReminderId?: string }) {
  const { t } = useTranslation("reminder");
  return (
    <div className="flex flex-col items-start gap-2 rounded-xl bg-warning-bg p-4" role="alert">
      <p className="text-body font-bold text-warning-text">{t("errors.limitReachedTitle")}</p>
      <p className="text-caption text-warning-text">{t("errors.limitReachedBody")}</p>
      <Link
        to={existingReminderId === undefined ? "/reminders" : `/reminders/${existingReminderId}`}
        className="text-caption font-bold text-primary-dark underline"
      >
        {existingReminderId === undefined ? t("errors.limitReachedFallback") : t("errors.limitReachedCta")}
      </Link>
    </div>
  );
}

export function ScheduleInvalidNotice() {
  const { t } = useTranslation("reminder");
  return (
    <div className="flex items-start gap-2 rounded-xl bg-danger-bg p-4" role="alert">
      <AlertTriangle size={16} className="mt-0.5 shrink-0 text-danger-text" aria-hidden="true" />
      <span>
        <span className="block text-body font-bold text-danger-text">{t("errors.scheduleInvalidTitle")}</span>
        <span className="block text-caption text-danger-text">{t("errors.scheduleInvalidBody")}</span>
      </span>
    </div>
  );
}

export function GenericSaveError({ message }: { message?: string }) {
  const { t } = useTranslation("reminder");
  return (
    <p className="flex items-start gap-1.5 text-caption text-danger-text" role="alert">
      <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
      {message ?? t("errors.saveFailed")}
    </p>
  );
}


export function EducationCard({ withPhoto = false }: { withPhoto?: boolean }) {
  const { t } = useTranslation("reminder");
  return (
    <section className="overflow-hidden rounded-2xl bg-chip-bg">
      {withPhoto ? (
        <img src={vetFollowupPhoto} alt={t("education.photoAlt")} className="h-36 w-full object-cover" />
      ) : null}
      <div className="flex flex-col gap-2 p-4">
        <p className="flex items-center gap-2 text-body font-bold text-primary-dark">
          <Bell size={16} aria-hidden="true" />
          {t("education.title")}
        </p>
        <p className="text-caption leading-relaxed text-text-secondary">{t("education.body")}</p>
        <p className="text-small leading-relaxed text-text-tertiary">{t("education.note")}</p>
        <Link to="/legal/medical-disclaimer" className="text-caption font-semibold text-primary-dark underline">
          {t("education.linkLabel")}
        </Link>
      </div>
    </section>
  );
}


const channelEnum = z.enum(REMINDER_CHANNELS);

export const reminderFormSchema = z
  .object({
    catId: z.string(),
    scheduleMode: z.enum(["INTERVAL", "RRULE"]),
    intervalDays: z.string(),
    rrule: z.string(),
    preferredTimeStart: z.string(),
    preferredTimeEnd: z.string(),
    channels: z.array(channelEnum).min(1, "form.channelRequired"),
    active: z.boolean(),
    requireCat: z.boolean(),
  })
  .superRefine((values, ctx) => {
    if (values.requireCat && values.catId === "") {
      ctx.addIssue({ code: "custom", message: "form.catRequired", path: ["catId"] });
    }
    if (values.scheduleMode === "INTERVAL") {
      const days = Number(values.intervalDays);
      if (
        values.intervalDays === "" ||
        !Number.isInteger(days) ||
        days < INTERVAL_DAYS_MIN ||
        days > INTERVAL_DAYS_MAX
      ) {
        ctx.addIssue({ code: "custom", message: "form.intervalRange", path: ["intervalDays"] });
      }
    } else if (values.rrule.trim() === "") {
      ctx.addIssue({ code: "custom", message: "form.rruleRequired", path: ["rrule"] });
    }

    const hasStart = values.preferredTimeStart !== "";
    const hasEnd = values.preferredTimeEnd !== "";
    if (hasStart !== hasEnd) {
      ctx.addIssue({ code: "custom", message: "form.timeBoth", path: ["preferredTimeEnd"] });
    } else if (hasStart && values.preferredTimeEnd <= values.preferredTimeStart) {
      ctx.addIssue({ code: "custom", message: "form.timeOrder", path: ["preferredTimeEnd"] });
    }
  });

export type ReminderFormValues = z.infer<typeof reminderFormSchema>;

export const DEFAULT_REMINDER_FORM: ReminderFormValues = {
  catId: "",
  scheduleMode: "INTERVAL",
  intervalDays: "3",
  rrule: "",
  preferredTimeStart: "08:00",
  preferredTimeEnd: "10:00",
  channels: ["PUSH", "IN_APP"],
  active: true,
  requireCat: true,
};

export function toReminderForm(reminder: Reminder): ReminderFormValues {
  return {
    catId: reminder.catId ?? "",
    scheduleMode: reminder.scheduleKind,
    intervalDays: reminder.intervalDays === undefined ? "" : String(reminder.intervalDays),
    rrule: reminder.rrule ?? "",
    preferredTimeStart: apiTimeToInput(reminder.preferredTimeStart),
    preferredTimeEnd: apiTimeToInput(reminder.preferredTimeEnd),
    channels: reminder.channels ?? [],
    active: reminder.active,
    requireCat: false,
  };
}

function sameChannels(a: ReminderChannel[], b: ReminderChannel[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((channel) => b.includes(channel));
}

export function buildReminderPatch(values: ReminderFormValues, reminder: Reminder): PatchReminderPayload {
  const patch: PatchReminderPayload = {};

  if (values.scheduleMode !== reminder.scheduleKind) {
    patch.scheduleKind = values.scheduleMode;
  }
  if (values.scheduleMode === "INTERVAL") {
    const days = Number(values.intervalDays);
    if (days !== reminder.intervalDays) patch.intervalDays = days;
  } else if (values.rrule.trim() !== (reminder.rrule ?? "")) {
    patch.rrule = values.rrule.trim();
  }

  if (values.preferredTimeStart !== apiTimeToInput(reminder.preferredTimeStart)) {
    patch.preferredTimeStart = inputTimeToApi(values.preferredTimeStart);
  }
  if (values.preferredTimeEnd !== apiTimeToInput(reminder.preferredTimeEnd)) {
    patch.preferredTimeEnd = inputTimeToApi(values.preferredTimeEnd);
  }

  if (!sameChannels(values.channels, reminder.channels ?? [])) {
    patch.channels = values.channels;
  }
  if (values.active !== reminder.active) {
    patch.active = values.active;
  }
  return patch;
}

export function firstFieldErrors(error: z.ZodError): Record<string, string> {
  const result: Record<string, string> = {};
  for (const issue of error.issues) {
    const key = issue.path[0];
    if (typeof key === "string" && !(key in result)) result[key] = issue.message;
  }
  return result;
}


const LEGEND_CLASS = "float-left flex w-full flex-wrap items-center justify-between gap-2";

function FieldError({ messageKey }: { messageKey?: string }) {
  const { t } = useTranslation("reminder");
  if (messageKey === undefined) return null;
  return (
    <p className="text-small text-danger-text" role="alert">
      {t(messageKey)}
    </p>
  );
}

export interface FrequencyPickerProps {
  scheduleMode: ReminderFormValues["scheduleMode"];
  intervalDays: string;
  rrule: string;
  errorKeyInterval?: string;
  errorKeyRrule?: string;
  headerAction?: ReactNode;
  onIntervalChange: (value: string) => void;
  onRruleChange: (value: string) => void;
  onModeChange: (mode: ReminderFormValues["scheduleMode"]) => void;
}

export function FrequencyPicker({
  scheduleMode,
  intervalDays,
  rrule,
  errorKeyInterval,
  errorKeyRrule,
  headerAction,
  onIntervalChange,
  onRruleChange,
  onModeChange,
}: FrequencyPickerProps) {
  const { t } = useTranslation("reminder");
  const customId = useId();
  const rruleId = useId();
  const advancedId = useId();

  const numeric = Number(intervalDays);
  const isPreset = PRESET_INTERVALS.some((value) => value === numeric);
  const presetLabels: Record<(typeof PRESET_INTERVALS)[number], { title: string; note: string }> = {
    1: { title: t("form.frequencyDaily"), note: t("form.frequencyDailyNote") },
    3: { title: t("form.frequencyEvery3"), note: t("form.frequencyEvery3Note") },
    7: { title: t("form.frequencyWeekly"), note: t("form.frequencyWeeklyNote") },
  };

  return (
    <fieldset className="flex flex-col gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
      <legend className={LEGEND_CLASS}>
        <span className="flex items-center gap-2 text-h3 font-bold text-text-primary">
          <Repeat size={18} className="text-primary-dark" aria-hidden="true" />
          {t("form.frequencyTitle")}
        </span>
        {headerAction}
      </legend>
      <p className="text-caption text-text-secondary">{t("form.frequencyHint")}</p>

      <div className="grid grid-cols-2 gap-3">
        {PRESET_INTERVALS.map((value) => {
          const selected = scheduleMode === "INTERVAL" && numeric === value;
          return (
            <button
              key={value}
              type="button"
              aria-pressed={selected}
              onClick={() => {
                onModeChange("INTERVAL");
                onIntervalChange(String(value));
              }}
              className={cn(
                "flex min-h-[72px] flex-col items-start justify-center gap-0.5 rounded-xl px-4 py-3 text-left transition-colors",
                selected
                  ? "bg-primary-dark text-white shadow-brand-lg"
                  : "bg-background-alt text-text-primary hover:bg-chip-bg",
              )}
            >
              <span className="flex w-full items-center justify-between gap-2">
                <span className="text-body font-bold">{presetLabels[value].title}</span>
                {selected ? <BadgeCheck size={16} className="shrink-0 text-secondary" aria-hidden="true" /> : null}
              </span>
              <span
                className={cn(
                  "text-small",
                  selected
                    ? "rounded-full bg-secondary px-2 py-0.5 font-bold text-secondary-text-on"
                    : "text-text-secondary",
                )}
              >
                {presetLabels[value].note}
              </span>
            </button>
          );
        })}

        <button
          type="button"
          aria-pressed={scheduleMode === "INTERVAL" && !isPreset}
          onClick={() => {
            onModeChange("INTERVAL");
            if (isPreset) onIntervalChange("");
          }}
          className={cn(
            "flex min-h-[72px] flex-col items-start justify-center gap-0.5 rounded-xl px-4 py-3 text-left transition-colors",
            scheduleMode === "INTERVAL" && !isPreset
              ? "bg-primary-dark text-white shadow-brand-lg"
              : "bg-background-alt text-text-primary hover:bg-chip-bg",
          )}
        >
          <span className="flex w-full items-center justify-between gap-2">
            <span className="text-body font-bold">{t("form.frequencyCustom")}</span>
            {scheduleMode === "INTERVAL" && !isPreset ? (
              <BadgeCheck size={16} className="shrink-0 text-secondary" aria-hidden="true" />
            ) : null}
          </span>
          <span
            className={cn(
              "text-small",
              scheduleMode === "INTERVAL" && !isPreset
                ? "rounded-full bg-secondary px-2 py-0.5 font-bold text-secondary-text-on"
                : "text-text-secondary",
            )}
          >
            {t("form.frequencyCustomNote")}
          </span>
        </button>
      </div>

      {scheduleMode === "INTERVAL" ? (
        <div className="flex flex-col gap-1.5">
          <label htmlFor={customId} className="text-caption font-semibold text-text-secondary">
            {t("form.intervalLabel")}
          </label>
          <input
            id={customId}
            type="number"
            inputMode="numeric"
            min={INTERVAL_DAYS_MIN}
            max={INTERVAL_DAYS_MAX}
            value={intervalDays}
            onChange={(event) => {
              onIntervalChange(event.target.value);
            }}
            aria-invalid={errorKeyInterval === undefined ? undefined : true}
            className="h-12 w-full rounded-xl bg-background-alt px-4 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          />
          <p className="text-small text-text-tertiary">
            {t("form.intervalHelper", { min: INTERVAL_DAYS_MIN, max: INTERVAL_DAYS_MAX })}
          </p>
          <FieldError messageKey={errorKeyInterval} />
        </div>
      ) : null}

      <div className="flex flex-col gap-2 rounded-xl bg-background-alt p-3">
        <label htmlFor={advancedId} className="flex items-center gap-2.5">
          <input
            id={advancedId}
            type="checkbox"
            checked={scheduleMode === "RRULE"}
            onChange={(event) => {
              onModeChange(event.target.checked ? "RRULE" : "INTERVAL");
            }}
            className="size-5 shrink-0 accent-[var(--color-primary-dark)]"
          />
          <span className="text-caption font-semibold text-text-primary">{t("form.advancedToggle")}</span>
        </label>
        {scheduleMode === "RRULE" ? (
          <div className="flex flex-col gap-1.5">
            <label htmlFor={rruleId} className="text-caption font-semibold text-text-secondary">
              {t("form.rruleLabel")}
            </label>
            <input
              id={rruleId}
              type="text"
              value={rrule}
              onChange={(event) => {
                onRruleChange(event.target.value);
              }}
              aria-invalid={errorKeyRrule === undefined ? undefined : true}
              className="h-12 w-full rounded-xl bg-surface px-4 font-mono text-caption text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
            />
            <p className="text-small text-text-tertiary">{t("form.advancedHint")}</p>
            <FieldError messageKey={errorKeyRrule} />
          </div>
        ) : null}
      </div>
    </fieldset>
  );
}

export interface TimeWindowPickerProps {
  start: string;
  end: string;
  timezone?: string;
  errorKey?: string;
  headerAction?: ReactNode;
  onStartChange: (value: string) => void;
  onEndChange: (value: string) => void;
}

export function TimeWindowPicker({
  start,
  end,
  timezone,
  errorKey,
  headerAction,
  onStartChange,
  onEndChange,
}: TimeWindowPickerProps) {
  const { t } = useTranslation("reminder");
  const startId = useId();
  const endId = useId();

  return (
    <fieldset className="flex flex-col gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
      <legend className={LEGEND_CLASS}>
        <span className="flex items-center gap-2 text-h3 font-bold text-text-primary">
          <Clock size={18} className="text-primary-dark" aria-hidden="true" />
          {t("form.timeTitle")}
        </span>
        {headerAction}
      </legend>
      <p className="text-caption text-text-secondary">{t("form.timeHint")}</p>

      {start === "" || end === "" ? null : (
        <div className="flex items-center gap-3 rounded-xl bg-background-alt p-3">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-secondary text-secondary-text-on">
            <Sun size={18} aria-hidden="true" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block text-caption font-bold text-text-primary">{t("form.timeSummaryTitle")}</span>
            <span className="block text-small text-text-secondary">
              {t("form.timeSummaryRange", { from: start, to: end })}
            </span>
          </span>
        </div>
      )}

      <div className="flex gap-3">
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <label htmlFor={startId} className="text-caption font-semibold text-text-secondary">
            {t("form.timeStartLabel")}
          </label>
          <input
            id={startId}
            type="time"
            value={start}
            onChange={(event) => {
              onStartChange(event.target.value);
            }}
            className="h-12 w-full rounded-xl bg-background-alt px-4 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          />
        </div>
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <label htmlFor={endId} className="text-caption font-semibold text-text-secondary">
            {t("form.timeEndLabel")}
          </label>
          <input
            id={endId}
            type="time"
            value={end}
            onChange={(event) => {
              onEndChange(event.target.value);
            }}
            aria-invalid={errorKey === undefined ? undefined : true}
            className="h-12 w-full rounded-xl bg-background-alt px-4 text-body text-text-primary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
          />
        </div>
      </div>
      <FieldError messageKey={errorKey} />
      {timezone === undefined ? null : (
        <p className="text-small text-text-tertiary">{t("form.timezoneNote", { zone: timezone })}</p>
      )}
    </fieldset>
  );
}

const CHANNEL_ICON: Record<ReminderChannel, typeof Bell> = {
  PUSH: Smartphone,
  EMAIL: Mail,
  IN_APP: Bell,
};

export interface ChannelPickerProps {
  value: ReminderChannel[];
  errorKey?: string;
  onChange: (next: ReminderChannel[]) => void;
}

export function ChannelPicker({ value, errorKey, onChange }: ChannelPickerProps) {
  const { t } = useTranslation("reminder");
  return (
    <fieldset className="flex flex-col gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
      <legend className={LEGEND_CLASS}>
        <span className="flex items-center gap-2 text-h3 font-bold text-text-primary">
          <Bell size={18} className="text-primary-dark" aria-hidden="true" />
          {t("form.channelTitle")}
        </span>
      </legend>
      <p className="text-caption text-text-secondary">{t("form.channelHint")}</p>

      <ul className="flex flex-col gap-2">
        {REMINDER_CHANNELS.map((channel) => {
          const Icon = CHANNEL_ICON[channel];
          const checked = value.includes(channel);
          return (
            <li key={channel}>
              <label
                className={cn(
                  "flex min-h-14 cursor-pointer items-center gap-3 rounded-xl px-3 py-2.5 transition-colors",
                  checked ? "bg-chip-bg" : "bg-background-alt hover:bg-chip-bg",
                )}
              >
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-surface text-primary-dark">
                  <Icon size={17} aria-hidden="true" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block text-caption font-bold text-text-primary">{t(`channel.${channel}`)}</span>
                  <span className="block text-small text-text-secondary">{t(`channel.${channel}Note`)}</span>
                </span>
                <input
                  type="checkbox"
                  checked={checked}
                  onChange={(event) => {
                    onChange(event.target.checked ? [...value, channel] : value.filter((item) => item !== channel));
                  }}
                  className="size-5 shrink-0 accent-[var(--color-primary-dark)]"
                />
              </label>
            </li>
          );
        })}
      </ul>
      <p className="text-small text-text-tertiary">{t("form.channelPhase1Note")}</p>
      <FieldError messageKey={errorKey} />
    </fieldset>
  );
}

export interface CatSelectProps {
  value: string;
  options: { id: string; name: string }[];
  disabled?: boolean;
  loading?: boolean;
  errorKey?: string;
  onChange: (catId: string) => void;
}

export function CatSelect({ value, options, disabled = false, loading = false, errorKey, onChange }: CatSelectProps) {
  const { t } = useTranslation("reminder");
  const selectId = useId();

  if (loading) {
    return <p className="text-caption text-text-secondary">{t("form.catLoading")}</p>;
  }

  if (options.length === 0) {
    return (
      <div className="flex flex-col items-start gap-2 rounded-2xl bg-surface p-4 shadow-brand-md">
        <p className="text-caption text-text-secondary">{t("form.catEmpty")}</p>
        <Link to="/cats/new" className="text-caption font-bold text-primary-dark underline">
          {t("form.catEmptyCta")}
        </Link>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5 rounded-2xl bg-surface p-4 shadow-brand-md">
      <label htmlFor={selectId} className="text-caption font-semibold text-text-secondary">
        {t("form.catLabel")}
      </label>
      <select
        id={selectId}
        value={value}
        disabled={disabled}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        aria-invalid={errorKey === undefined ? undefined : true}
        className="h-12 w-full rounded-xl bg-background-alt px-4 text-body text-text-primary disabled:opacity-60 focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
      >
        <option value="">{t("form.catPlaceholder")}</option>
        {options.map((cat) => (
          <option key={cat.id} value={cat.id}>
            {cat.name}
          </option>
        ))}
      </select>
      {disabled ? <p className="text-small text-text-tertiary">{t("form.catLocked")}</p> : null}
      <FieldError messageKey={errorKey} />
    </div>
  );
}

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <header className="flex flex-col gap-1">
      <div className="flex items-center justify-between gap-3">
        <h1 className="min-w-0 text-h2 font-bold text-text-primary lg:text-h1">{title}</h1>
        {action}
      </div>
      {subtitle === undefined ? null : <p className="text-caption text-text-secondary lg:text-body">{subtitle}</p>}
    </header>
  );
}

export function AddToCalendarLink({ href, className, label }: { href: string; className?: string; label?: string }) {
  const { t } = useTranslation("reminder");
  return (
    <a
      href={href}
      download
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-caption font-semibold text-primary-dark hover:bg-background-alt",
        className,
      )}
    >
      <CalendarDays size={15} aria-hidden="true" />
      {label ?? t("list.addToCalendar")}
    </a>
  );
}
