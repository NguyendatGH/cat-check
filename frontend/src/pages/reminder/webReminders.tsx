import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { CalendarClock, Camera, ChevronRight, Pause, Play } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { ErrorState, SkeletonLoader } from "@/shared/ui";
import { reminderCalendarUrl, type Reminder } from "@/features/reminder";
import { ReminderQuickPlan } from "./ReminderQuickPlan";
import {
  AddToCalendarLink,
  DueChip,
  EducationCard,
  FeatureLockedCard,
  StatusChip,
  dueState,
  formatDateTime,
  timeWindow,
  useScheduleLabel,
} from "./reminderUi";

/**
 * Bố cục DESKTOP (>= lg) của `/reminders` — dựng từ bản export gốc
 * `Web - 09. Trung tâm Nhắc nhở & Lịch Y tế (Monitoring Reminders)` (khung 1280px).
 *
 * KHÔNG tự thêm padding ngang ở `lg` — `TaskLayout` đã cấp hộp nội dung 944px có sẵn padding.
 *
 * ĐÃ BỎ so với mockup, vì KHÔNG có endpoint và dựng lên sẽ là dữ liệu bịa:
 *  - cột thiết bị IoT "CleanBox Smart-01" / "Bio-Kit C10 Pro" (CatCheck Phase 1 quét bằng ảnh,
 *    không có phần cứng);
 *  - các thẻ "đặt mua cát giao 2H", "kho dự trữ tại nhà", "tái khám tại phòng khám X",
 *    "nhắc nhỏ gáy/tẩy giun" (không thuộc miền `reminder`, và phần lớn vi phạm luật câu chữ);
 *  - khối "AI Auto-Tuning" (không có backend).
 * Mọi con số hiển thị ở đây đều suy từ danh sách `reminders` thật của I1.
 */

const WEEKDAY_KEYS = [
  "web.weekday1",
  "web.weekday2",
  "web.weekday3",
  "web.weekday4",
  "web.weekday5",
  "web.weekday6",
  "web.weekday7",
] as const;

export type ReminderTab = "ALL" | "SCAN" | "OTHER";

const TAB_ORDER: ReminderTab[] = ["ALL", "SCAN", "OTHER"];
const TAB_LABEL_KEY: Record<ReminderTab, string> = {
  ALL: "web.tabAll",
  SCAN: "web.tabScan",
  OTHER: "web.tabOther",
};

export function matchesTab(reminder: Reminder, tab: ReminderTab): boolean {
  if (tab === "ALL") return true;
  return tab === "SCAN" ? reminder.type === "SCAN_ROUTINE" : reminder.type !== "SCAN_ROUTINE";
}

/** Ô ngày của lịch tháng — `day === null` là ô đệm đầu tuần. */
interface CalendarCell {
  day: number | null;
  marked: boolean;
  isToday: boolean;
}

/** Lịch tháng hiện tại, tuần bắt đầu thứ Hai. Chấm = ngày có `nextRunAt` của một lịch đang bật. */
function buildMonthCells(reminders: Reminder[], now: Date): CalendarCell[] {
  const year = now.getFullYear();
  const month = now.getMonth();
  const first = new Date(year, month, 1);
  // getDay(): 0 = CN. Chuyển sang tuần bắt đầu thứ Hai (0 = T2 … 6 = CN).
  const leading = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const marked = new Set<number>();
  for (const reminder of reminders) {
    if (reminder.nextRunAt === undefined) continue;
    const at = new Date(reminder.nextRunAt);
    if (at.getFullYear() === year && at.getMonth() === month) marked.add(at.getDate());
  }

  const cells: CalendarCell[] = [];
  for (let i = 0; i < leading; i += 1) cells.push({ day: null, marked: false, isToday: false });
  for (let day = 1; day <= daysInMonth; day += 1) {
    cells.push({ day, marked: marked.has(day), isToday: day === now.getDate() });
  }
  return cells;
}

function StatCard({ label, value, tone }: { label: string; value: number; tone: "brand" | "danger" }) {
  return (
    <div className="flex flex-col gap-1 rounded-2xl bg-surface p-4 shadow-xs">
      <p className="text-overline font-bold tracking-[0.4px] text-text-secondary">{label}</p>
      <span
        className={cn(
          "text-[26px] font-bold leading-8",
          tone === "danger" ? "text-danger-text" : "text-primary-dark",
        )}
      >
        {value}
      </span>
    </div>
  );
}

export interface WebRemindersScreenProps {
  reminders: Reminder[];
  catNameById: Map<string, string>;
  isPending: boolean;
  /** 403 `FEATURE_NOT_IN_PLAN`. */
  locked: boolean;
  isError: boolean;
  tab: ReminderTab;
  onTabChange: (tab: ReminderTab) => void;
  onToggle: (reminder: Reminder) => void;
  togglingId: string | null;
  onRetry: () => void;
}

export function WebRemindersScreen({
  reminders,
  catNameById,
  isPending,
  locked,
  isError,
  tab,
  onTabChange,
  onToggle,
  togglingId,
  onRetry,
}: WebRemindersScreenProps) {
  const { t } = useTranslation(["reminder", "common"]);
  const scheduleLabel = useScheduleLabel();
  const now = new Date();

  const header = (
    <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">
      <div className="max-w-[620px]">
        <p className="text-overline font-bold tracking-[0.4px] text-primary-dark">
          {t("web.eyebrow")}
        </p>
        <h1 className="pt-1 text-[28px] font-bold leading-9 tracking-[-0.5px] text-primary-dark">
          {t("web.title")}
        </h1>
        <p className="pt-2 text-caption leading-relaxed text-text-secondary">{t("web.subtitle")}</p>
      </div>
      <Link
        to="/reminders/new"
        className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-2.5 text-caption font-bold text-white shadow-sm"
      >
        {t("web.newCta")}
      </Link>
    </div>
  );

  if (locked) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <FeatureLockedCard />
      </div>
    );
  }

  if (isError) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <ErrorState
          title={t("list.loadError")}
          onRetry={onRetry}
          retryLabel={t("actions.retry", { ns: "common" })}
        />
      </div>
    );
  }

  if (isPending) {
    return (
      <div className="flex flex-col gap-4">
        {header}
        <SkeletonLoader shape="card" className="h-24" />
        <SkeletonLoader shape="card" className="h-56" />
      </div>
    );
  }

  const visible = reminders.filter((reminder) => matchesTab(reminder, tab));
  const active = visible.filter((reminder) => reminder.active);
  const paused = visible.filter((reminder) => !reminder.active);
  const dueToday = active.filter((reminder) => {
    const due = dueState(reminder.nextRunAt, now.getTime());
    return due.kind === "overdue" || due.kind === "hours";
  });
  const upcoming = active.filter((reminder) => !dueToday.includes(reminder));
  const overdueCount = reminders.filter(
    (reminder) => reminder.active && dueState(reminder.nextRunAt, now.getTime()).kind === "overdue",
  ).length;

  const cells = buildMonthCells(active, now);
  const monthLabel = now.toLocaleDateString("vi-VN", { month: "long", year: "numeric" });

  const tabCount = (value: ReminderTab) =>
    reminders.filter((reminder) => matchesTab(reminder, value)).length;

  const catLabel = (reminder: Reminder) =>
    reminder.catId === undefined
      ? t("list.accountWide")
      : (catNameById.get(reminder.catId) ?? t("list.unknownCat"));

  return (
    <div className="flex flex-col gap-4">
      {header}

      <div className="grid grid-cols-3 gap-4">
        <StatCard label={t("web.statTotal")} value={reminders.length} tone="brand" />
        <StatCard
          label={t("web.statActive")}
          value={reminders.filter((reminder) => reminder.active).length}
          tone="brand"
        />
        <StatCard label={t("web.statOverdue")} value={overdueCount} tone="danger" />
      </div>

      <div className="flex flex-wrap gap-2">
        {TAB_ORDER.map((value) => (
          <button
            key={value}
            type="button"
            aria-pressed={value === tab}
            onClick={() => {
              onTabChange(value);
            }}
            className={cn(
              "inline-flex items-center gap-2 rounded-xl px-4 py-2.5 text-caption font-semibold transition-colors",
              value === tab
                ? "bg-primary-dark text-white"
                : "bg-surface text-text-secondary hover:bg-background-alt",
            )}
          >
            {t(TAB_LABEL_KEY[value])}
            <span
              className={cn(
                "rounded-full px-2 py-0.5 text-small font-bold",
                value === tab ? "bg-on-primary-muted text-primary-dark" : "bg-chip-bg text-primary-dark",
              )}
            >
              {tabCount(value)}
            </span>
          </button>
        ))}
      </div>

      <div className="grid grid-cols-12 gap-4">
        {/* ── Cột chính ───────────────────────────────────────────────────────────────── */}
        <div className="col-span-12 flex flex-col gap-4 xl:col-span-8">
          <section className="flex flex-col gap-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <h2 className="flex items-center gap-2 text-[18px] font-bold leading-6 text-primary-dark">
                <span className="size-2 rounded-full bg-secondary" aria-hidden="true" />
                {t("web.dueTodayTitle")}
              </h2>
              {dueToday.length > 0 ? (
                <span className="rounded-full bg-danger-bg px-3 py-1 text-small font-bold text-danger-text">
                  {t("web.dueTodayCount", { count: dueToday.length })}
                </span>
              ) : null}
            </div>

            {dueToday.length === 0 ? (
              <p className="rounded-2xl bg-surface p-4 text-caption text-text-secondary shadow-xs">
                {t("web.dueTodayEmpty")}
              </p>
            ) : (
              dueToday.map((reminder) => {
                const due = dueState(reminder.nextRunAt, now.getTime());
                const window = timeWindow(reminder);
                return (
                  <article
                    key={reminder.id}
                    className="flex gap-4 rounded-2xl border-l-4 border-secondary bg-surface p-4 shadow-xs"
                  >
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-secondary/30 text-secondary-text-on">
                      <Camera size={18} aria-hidden="true" />
                    </span>
                    <div className="flex min-w-0 flex-1 flex-col gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        {window === null ? null : (
                          <span className="rounded-full bg-secondary/30 px-2.5 py-1 text-small font-bold text-secondary-text-on">
                            {t("web.timeWindowChip", { from: window.from, to: window.to })}
                          </span>
                        )}
                        <span className="text-small text-text-secondary">
                          {t("web.forCat", { name: catLabel(reminder) })}
                        </span>
                      </div>
                      <p className="text-body font-bold leading-snug text-text-primary">
                        {t(`type.${reminder.type}`)}
                      </p>
                      <p className="text-caption text-text-secondary">
                        {reminder.nextRunAt === undefined
                          ? t("list.nextRunUnknown")
                          : `${t("list.nextRunLabel")}: ${formatDateTime(reminder.nextRunAt)}`}
                      </p>
                      <div className="flex flex-wrap items-center gap-2 pt-1">
                        <DueChip due={due} />
                        <Link
                          to="/scan"
                          className="inline-flex items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-2 text-caption font-bold text-white shadow-sm"
                        >
                          {t("web.scanNowCta")}
                        </Link>
                        <button
                          type="button"
                          disabled={togglingId === reminder.id}
                          onClick={() => {
                            onToggle(reminder);
                          }}
                          className="inline-flex items-center justify-center gap-2 rounded-xl border border-border bg-surface px-4 py-2 text-caption font-semibold text-text-secondary hover:bg-background-alt disabled:opacity-50"
                        >
                          <Pause size={14} aria-hidden="true" />
                          {t("web.pauseCta")}
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })
            )}
          </section>

          <section className="flex flex-col gap-3">
            <h2 className="text-[18px] font-bold leading-6 text-primary-dark">
              {t("web.upcomingTitle")}
            </h2>
            {upcoming.length === 0 ? (
              <p className="rounded-2xl bg-surface p-4 text-caption text-text-secondary shadow-xs">
                {t("web.upcomingEmpty")}
              </p>
            ) : (
              upcoming.map((reminder) => (
                <article
                  key={reminder.id}
                  className="flex flex-wrap items-center gap-4 rounded-2xl bg-surface p-4 shadow-xs"
                >
                  <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
                    <CalendarClock size={18} aria-hidden="true" />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-body font-bold text-text-primary">{catLabel(reminder)}</p>
                      <StatusChip reminder={reminder} due={dueState(reminder.nextRunAt, now.getTime())} />
                    </div>
                    <p className="text-caption text-text-secondary">
                      {`${scheduleLabel(reminder)} · ${t(`type.${reminder.type}`)}`}
                    </p>
                    <p className="text-small text-text-tertiary">
                      {reminder.nextRunAt === undefined
                        ? t("list.nextRunUnknown")
                        : `${t("list.nextRunLabel")}: ${formatDateTime(reminder.nextRunAt)}`}
                    </p>
                  </div>
                  <div className="flex shrink-0 flex-wrap items-center gap-2">
                    <DueChip due={dueState(reminder.nextRunAt, now.getTime())} />
                    <AddToCalendarLink href={reminderCalendarUrl(reminder.id)} />
                    <Link
                      to={`/reminders/${reminder.id}`}
                      className="inline-flex items-center justify-center gap-1 rounded-xl bg-primary-dark px-4 py-2.5 text-caption font-bold text-white shadow-sm"
                    >
                      {t("web.editCta")}
                      <ChevronRight size={14} aria-hidden="true" />
                    </Link>
                  </div>
                </article>
              ))
            )}
          </section>

          {paused.length > 0 ? (
            <section className="flex flex-col gap-3">
              <h2 className="text-[18px] font-bold leading-6 text-text-secondary">
                {t("web.pausedTitle")}
              </h2>
              {paused.map((reminder) => (
                <article
                  key={reminder.id}
                  className="flex flex-wrap items-center gap-4 rounded-2xl bg-surface/70 p-4 shadow-xs"
                >
                  <div className="min-w-0 flex-1">
                    <p className="text-body font-bold text-text-secondary">{catLabel(reminder)}</p>
                    <p className="text-caption text-text-tertiary">
                      {`${scheduleLabel(reminder)} · ${t(`type.${reminder.type}`)}`}
                    </p>
                  </div>
                  <button
                    type="button"
                    disabled={togglingId === reminder.id}
                    onClick={() => {
                      onToggle(reminder);
                    }}
                    className="inline-flex shrink-0 items-center justify-center gap-2 rounded-xl border border-border bg-surface px-4 py-2.5 text-caption font-semibold text-primary-dark hover:bg-background-alt disabled:opacity-50"
                  >
                    <Play size={14} aria-hidden="true" />
                    {t("web.resumeCta")}
                  </button>
                  <Link
                    to={`/reminders/${reminder.id}`}
                    className="inline-flex shrink-0 items-center justify-center gap-1 text-caption font-semibold text-primary-dark underline"
                  >
                    {t("web.editCta")}
                  </Link>
                </article>
              ))}
            </section>
          ) : null}

          {/* Panel "Cấu hình Tần suất" ở chân cột chính của `Web - 09` — cùng khối sửa nhanh
              với bản mobile. */}
          <ReminderQuickPlan reminders={reminders} catNameById={catNameById} />
        </div>

        {/* ── Cột phải ────────────────────────────────────────────────────────────────── */}
        <div className="col-span-12 flex flex-col gap-4 xl:col-span-4">
          <section className="flex flex-col gap-3 rounded-2xl bg-surface p-4 shadow-xs">
            <p className="text-body font-bold capitalize text-primary-dark">{monthLabel}</p>
            <div className="grid grid-cols-7 gap-1 text-center">
              {WEEKDAY_KEYS.map((key) => (
                <span key={key} className="py-1 text-small font-semibold text-text-tertiary">
                  {t(key)}
                </span>
              ))}
              {cells.map((cell, index) => (
                <span
                  key={cell.day ?? `pad-${String(index)}`}
                  className={cn(
                    "relative flex h-8 items-center justify-center rounded-lg text-caption",
                    cell.day === null && "opacity-0",
                    cell.isToday
                      ? "bg-primary-dark font-bold text-white"
                      : "text-text-secondary",
                  )}
                >
                  {cell.day ?? ""}
                  {cell.marked && !cell.isToday ? (
                    <span
                      className="absolute bottom-0.5 size-1.5 rounded-full bg-secondary"
                      aria-hidden="true"
                    />
                  ) : null}
                </span>
              ))}
            </div>
            <p className="text-small leading-relaxed text-text-tertiary">
              {cells.some((cell) => cell.marked) ? t("web.calendarLegend") : t("web.calendarEmpty")}
            </p>
          </section>

          <EducationCard withPhoto />
        </div>
      </div>
    </div>
  );
}
