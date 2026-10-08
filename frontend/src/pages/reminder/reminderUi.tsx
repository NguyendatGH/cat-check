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

/**
 * Mảnh dùng chung của ba trang `/reminders*` — formatter, chip trạng thái, khối form lịch,
 * và ba trạng thái lỗi nghiệp vụ. Đặt cạnh trang (không nhét vào `features/reminder`) theo
 * đúng tiền lệ `pages/trends/webTrends.tsx`: đây là lớp trình bày riêng của màn này, còn
 * feature chỉ giữ hợp đồng API + hook.
 *
 * Nguồn thiết kế: frame `09. Nhắc nhở Theo dõi` (mobile 390px) và
 * `Web - 09. Trung tâm Nhắc nhở & Lịch Y tế` (desktop 1280px).
 */

// ─── Formatter ───────────────────────────────────────────────────────────────────────────

const HOUR_MS = 60 * 60 * 1000;
const DAY_MS = 24 * HOUR_MS;

/** Khoảng cách ngày ứng với 3 ô preset của frame 09 ("Mỗi ngày" / "Mỗi 3 ngày" / "Hàng tuần"). */
export const PRESET_INTERVALS = [1, 3, 7] as const;

/** `08:00 09/10/2026` — gọn hơn `dateStyle: "medium"` ("08:00 9 thg 10, 2026") vốn bị cắt `…` trong ô 390px. */
export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

/**
 * Nhãn "lần … tiếp theo" theo LOẠI lịch: chỉ `SCAN_ROUTINE` là nhắc QUÉT; `CREDIT_EXPIRY` /
 * `SURVEY_FOLLOWUP` là nhắc hệ thống — gọi chúng là "Lần quét tiếp theo" là nói sai.
 */
export function useNextRunLabel(): (reminder: Pick<Reminder, "type">) => string {
  const { t } = useTranslation("reminder");
  return (reminder) => (reminder.type === "SCAN_ROUTINE" ? t("list.nextRunLabel") : t("list.nextReminderLabel"));
}

/**
 * Hành động chính của một lịch đến hạn, theo loại lịch: nhắc quét → `/scan`; nhắc hạn gói →
 * `/credits`; nhắc khảo sát → trang chi tiết lịch. Trước đây mọi thẻ đều "Quét ngay", kể cả
 * lịch nhắc hạn gói.
 */
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

/**
 * Trạng thái "còn bao lâu tới hạn" suy từ `nextRunAt`.
 *
 * `nextRunAt` VẮNG MẶT khi lịch đang tắt (server bỏ field null) — trả `none`, KHÔNG đoán mốc.
 */
export function dueState(nextRunAt: string | undefined, now: number = Date.now()): DueState {
  if (nextRunAt === undefined) return { kind: "none" };
  const delta = new Date(nextRunAt).getTime() - now;
  if (Number.isNaN(delta)) return { kind: "none" };
  if (delta < 0) return { kind: "overdue", days: Math.floor(-delta / DAY_MS) };
  if (delta < DAY_MS) return { kind: "hours", hours: Math.max(1, Math.round(delta / HOUR_MS)) };
  return { kind: "days", days: Math.round(delta / DAY_MS) };
}

/** Câu mô tả nhịp lặp — ưu tiên nhãn quen thuộc cho 1 ngày / 7 ngày. */
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

/** Khung giờ ưu tiên dạng `08:00 – 10:00`, hoặc `null` nếu chưa đặt. */
export function timeWindow(reminder: Reminder): { from: string; to: string } | null {
  const { preferredTimeStart, preferredTimeEnd } = reminder;
  if (preferredTimeStart === undefined || preferredTimeEnd === undefined) return null;
  return { from: preferredTimeStart.slice(0, 5), to: preferredTimeEnd.slice(0, 5) };
}

// ─── Chip / nhãn ─────────────────────────────────────────────────────────────────────────

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

/** Viên "Còn 18 giờ" / "Đã quá hạn 4 ngày" của frame 09. */
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

/**
 * Pill cạnh tên mèo trong frame 09 (`#EBEDFF` = token `deco-backdrop`).
 *
 * Nội dung do nơi gọi quyết định và luôn là dữ liệu THẬT: giống mèo của D1
 * (`cat.breedName`/`breedOther`) nếu có, không thì loại lịch. KHÔNG bịa giống.
 */
export function BreedChip({ label }: { label: string }) {
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-deco-backdrop px-2.5 py-0.5 text-small font-semibold text-text-secondary">
      {label}
    </span>
  );
}

/** Pill đỏ "Cần quét kiểm tra" của thẻ quá hạn trong frame 09. */
export function NeedScanChip() {
  const { t } = useTranslation("reminder");
  return (
    <span className="inline-flex shrink-0 items-center rounded-full bg-danger-bg px-2.5 py-0.5 text-small font-bold text-danger-text">
      {t("list.needScan")}
    </span>
  );
}

/**
 * Dòng trạng thái MỘT DÒNG của thẻ mèo trong frame 09 ("✓ Đang theo dõi • Mỗi 3 ngày",
 * "Lịch: Hàng tuần (Tạm dừng)") — thay cho cặp chip + chữ rời trước đây.
 */
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

// ─── Trạng thái lỗi nghiệp vụ ────────────────────────────────────────────────────────────

/**
 * 403 `FEATURE_NOT_IN_PLAN` — dùng lại đúng cách xử lý của `pages/trends/webTrends.tsx`:
 * chip + câu `common:upsell.*` + CTA sang `/credits`.
 */
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

/**
 * 409 `REMINDER_LIMIT_REACHED` — mèo đã có lịch cùng loại đang bật. KHÔNG hiện như lỗi hệ
 * thống: dẫn thẳng sang lịch đang có để sửa (nếu tìm được id trong danh sách đã tải).
 */
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

/** 400 `REMINDER_SCHEDULE_INVALID` — server từ chối cấu hình lịch. */
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

/** Lỗi lưu không rơi vào 3 mã trên. */
export function GenericSaveError({ message }: { message?: string }) {
  const { t } = useTranslation("reminder");
  return (
    <p className="flex items-start gap-1.5 text-caption text-danger-text" role="alert">
      <AlertTriangle size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
      {message ?? t("errors.saveFailed")}
    </p>
  );
}

// ─── Khối nội dung tĩnh ──────────────────────────────────────────────────────────────────

/**
 * Khối giải thích ở chân frame 09 và cột phải của `Web - 09`.
 *
 * KHÔNG phải dữ liệu giả: ảnh lấy từ chính file thiết kế, câu chữ do dự án viết lại. Bản
 * mockup gốc hứa phát hiện tinh thể/tắc nghẽn và dẫn một tổ chức y khoa không có thật —
 * cả hai đều vi phạm quyết định #8 (chỉ đo pH) và luật câu chữ p17 §17.10b nên bị thay bằng
 * mô tả thuần quan sát + liên kết tới tuyên bố miễn trừ y tế.
 */
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

// ─── Schema form ─────────────────────────────────────────────────────────────────────────

const channelEnum = z.enum(REMINDER_CHANNELS);

/**
 * Giá trị form của cả trang tạo mới lẫn trang chi tiết. `message` là KEY i18n namespace
 * `reminder` (dịch lúc render bằng `t(message)`) — cùng quy ước `features/cat/schemas.ts`.
 *
 * Các ràng buộc ở đây PHẢN CHIẾU ràng buộc server (`@Min(1) @Max(90)`, `preferredTimeEnd >
 * preferredTimeStart`, phải có `catId` với `SCAN_ROUTINE`) để báo sớm, KHÔNG thay thế:
 * server vẫn là nơi quyết định và `REMINDER_SCHEDULE_INVALID` vẫn được render thành trạng thái.
 */
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
    /** `false` ở trang chi tiết: mèo đã gắn, không đổi được nên không bắt buộc chọn lại. */
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
  // Ô "Mỗi 3 ngày" là lựa chọn nổi bật của frame 09; không phải ngưỡng lâm sàng, chỉ là mặc định UI.
  intervalDays: "3",
  rrule: "",
  preferredTimeStart: "08:00",
  preferredTimeEnd: "10:00",
  channels: ["PUSH", "IN_APP"],
  active: true,
  requireCat: true,
};

/** Giá trị form suy từ một lịch nhắc đã lưu (dùng ở trang chi tiết và khối sửa nhanh). */
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
    // Mèo đã gắn lúc tạo và server không cho đổi — không bắt buộc chọn lại khi sửa.
    requireCat: false,
  };
}

function sameChannels(a: ReminderChannel[], b: ReminderChannel[]): boolean {
  if (a.length !== b.length) return false;
  return a.every((channel) => b.includes(channel));
}

/**
 * Dựng merge-patch TỐI THIỂU: chỉ field thật sự đổi mới có mặt (RFC 7396 — field vắng = giữ nguyên).
 *
 * HẠN CHẾ CÓ CHỦ Ý: không XOÁ được khung giờ đã đặt. Merge-patch cần `null` tường minh để xoá,
 * nhưng `PatchReminderRequest` phía server nhận `LocalTime` và không mô hình hoá `null`, nên UI
 * chỉ cho đổi khung giờ chứ không cho bỏ trống lại.
 */
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

/**
 * Lỗi đầu tiên của mỗi field, dạng `{ field: <key i18n> }`.
 *
 * Khối sửa nhanh ở `/reminders` không dùng react-hook-form (state của nó phải nhảy theo lịch
 * đang chọn, derive thẳng từ dữ liệu server gọn hơn là reset form), nên validate bằng chính
 * `reminderFormSchema` rồi gom lỗi ở đây để tái dùng đúng các key câu chữ sẵn có.
 */
export function firstFieldErrors(error: z.ZodError): Record<string, string> {
  const result: Record<string, string> = {};
  for (const issue of error.issues) {
    const key = issue.path[0];
    if (typeof key === "string" && !(key in result)) result[key] = issue.message;
  }
  return result;
}

// ─── Mảnh form ───────────────────────────────────────────────────────────────────────────

/**
 * `<legend>` mặc định được trình duyệt vẽ ĐÈ lên mép trên của `<fieldset>` (nằm ngoài padding)
 * ⇒ tiêu đề "Tần suất theo dõi" lòi nửa ra ngoài thẻ trắng. `float-left` biến nó thành phần tử
 * thường (không còn là "rendered legend"), nên trong fieldset `flex-col` nó nằm gọn trong thẻ
 * như frame 09 — vẫn giữ đúng ngữ nghĩa nhóm cho trình đọc màn hình.
 */
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
  /** Góc phải tiêu đề — frame 09 đặt "Kế hoạch cho {tên mèo}" ở đây. */
  headerAction?: ReactNode;
  onIntervalChange: (value: string) => void;
  onRruleChange: (value: string) => void;
  onModeChange: (mode: ReminderFormValues["scheduleMode"]) => void;
}

/** 4 ô "Tần suất Theo dõi" của frame 09 + ô nhập số ngày + lối thoát RRULE. */
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

/** Khối "Khung giờ Ưu tiên" của frame 09 — hai mốc `HH:mm`, server kiểm end > start. */
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

/**
 * "Kênh nhận thông báo" — CHỈ 3 kênh backend nhận (`PUSH`/`EMAIL`/`IN_APP`).
 *
 * Mockup còn vẽ "Rung nhẹ trên Đồng hồ thông minh" và "Tin nhắn SMS dự phòng": enum phía
 * server không có hai giá trị đó (gửi lên ⇒ `REMINDER_SCHEDULE_INVALID`), nên bỏ hẳn thay
 * vì vẽ ô bị khoá — vẽ ra sẽ hứa một tính năng không tồn tại.
 */
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

/** "Hồ sơ Mèo của bạn" ở đầu frame 09 — danh sách mèo lấy từ `features/cat` (D1), không endpoint mới. */
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

/** Tiêu đề trang dùng chung — mobile dùng `text-h2`, desktop nở lên `text-h1`. */
export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  // Nút phụ (VD "+ Thêm lịch nhắc") đứng cùng hàng với TIÊU ĐỀ; mô tả trải hết bề ngang bên
  // dưới. Trước đây mô tả nằm chung cột với tiêu đề nên ở 390px bị ép còn ~200px, xuống 4 dòng.
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

/**
 * Nút "Thêm vào lịch" (I6) — `<a download>` để trình duyệt giữ `Content-Disposition` của server.
 *
 * `label` cho phép dùng lại đúng nút này làm "Đồng bộ vào Lịch Apple / Google" ở chân frame 09:
 * cùng một file `.ics`, chỉ khác câu chữ — không có endpoint đồng bộ hai chiều nào ở Phase 1.
 */
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
