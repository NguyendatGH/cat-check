import { useState } from "react";
import { useTranslation } from "react-i18next";
import { addDays, format } from "date-fns";
import { vi } from "date-fns/locale";
import { CalendarCheck, CheckCircle2, Phone, Stethoscope } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { createPlaceBooking, placeErrorStatus, type PlaceApi } from "@/features/place";
import { telHref } from "./placeView";

/**
 * Form YÊU CẦU đặt lịch — gọi thật `POST /places/{id}/bookings` (202, trạng thái `REQUESTED`).
 *
 * API chỉ nhận `serviceCode`, `date`, `timeSlot`, `note`. Không có danh mục dịch vụ, lịch trống,
 * bác sĩ, giá hay chọn mèo — nên form không có các khối đó. "Dịch vụ" lấy từ chính
 * `specialties[]` cơ sở công bố, cộng một lựa chọn "khác" (mô tả trong ghi chú). Ô ngày là 8 ngày
 * tới (chỉ là lối tắt chọn ngày, không phải lịch trống của cơ sở); giờ là ô nhập tự do.
 */

const OTHER_SERVICE = "OTHER";
const SERVICE_CODE_MAX = 48;
const NOTE_MAX = 1000;
const DAY_COUNT = 8;

type SubmitState = "idle" | "submitting" | "success" | "slotTaken" | "auth" | "invalid" | "error";

type ValidationKey = "booking.validation.date" | "booking.validation.time" | "booking.validation.pastTime";

function serviceOptions(specialties: string[]): string[] {
  return [...new Set(specialties.map((s) => s.trim()).filter(Boolean))];
}

interface BookingPanelProps {
  place: PlaceApi;
  formId: string;
  /** `card`: khối nổi ở cột phải (desktop). `sheet`: nằm trong bottom sheet (mobile), tiêu đề do Sheet vẽ. */
  variant?: "card" | "sheet";
}

export function BookingPanel({ place, formId, variant = "card" }: BookingPanelProps) {
  const { t } = useTranslation("map");
  const services = serviceOptions(place.specialties);
  const [service, setService] = useState(services[0] ?? OTHER_SERVICE);
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
  const [note, setNote] = useState("");
  const [state, setState] = useState<SubmitState>("idle");
  const [validation, setValidation] = useState<ValidationKey | null>(null);
  const [sent, setSent] = useState<{ date: string; time: string } | null>(null);

  const today = new Date();
  const todayIso = format(today, "yyyy-MM-dd");
  const days = Array.from({ length: DAY_COUNT }, (_, i) => {
    const d = addDays(today, i);
    return {
      iso: format(d, "yyyy-MM-dd"),
      weekday: i === 0 ? t("booking.today") : format(d, "EEEEE", { locale: vi }),
      day: format(d, "dd/MM"),
    };
  });
  const phone = place.phone?.trim() ?? "";

  async function submit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    if (state === "submitting") return;
    if (!date) {
      setValidation("booking.validation.date");
      return;
    }
    if (!time) {
      setValidation("booking.validation.time");
      return;
    }
    if (date === todayIso && time <= format(new Date(), "HH:mm")) {
      setValidation("booking.validation.pastTime");
      return;
    }
    setValidation(null);
    setState("submitting");
    try {
      await createPlaceBooking(place.id, {
        serviceCode: service.slice(0, SERVICE_CODE_MAX),
        date,
        timeSlot: time,
        note: note.trim() || undefined,
      });
      setSent({ date, time });
      setState("success");
      setTime("");
      setNote("");
    } catch (error) {
      const status = placeErrorStatus(error);
      setState(
        status === 409 ? "slotTaken" : status === 401 || status === 403 ? "auth" : status === 400 ? "invalid" : "error",
      );
    }
  }

  const resultMessage =
    state === "success" && sent
      ? t("booking.requestSent", { date: format(new Date(`${sent.date}T00:00:00`), "dd/MM/yyyy"), time: sent.time })
      : state === "slotTaken"
        ? t("booking.slotTaken")
        : state === "auth"
          ? t("booking.authError")
          : state === "invalid"
            ? t("booking.invalidError")
            : state === "error"
              ? t("booking.requestError")
              : null;

  return (
    <form
      id={formId}
      noValidate
      onSubmit={(event) => {
        void submit(event);
      }}
      className={cn(variant === "card" && "rounded-2xl bg-surface p-5 shadow-brand-lg")}
    >
      {variant === "card" ? (
        <>
          <h2 className="flex items-center gap-2 text-[16px] font-bold text-primary-dark">
            <span className="size-2.5 shrink-0 rounded-full bg-success" aria-hidden="true" />
            {t("booking.title")}
          </h2>
          <p className="pt-1.5 text-[12px] leading-relaxed text-text-secondary">{t("booking.subtitle")}</p>
        </>
      ) : null}

      <fieldset className={cn("min-w-0", variant === "card" && "mt-4")}>
        <legend className="mb-2 text-[12px] font-bold text-text-primary">{t("booking.serviceLabel")}</legend>
        <div className="grid grid-cols-2 gap-2">
          {[...services, OTHER_SERVICE].map((s) => {
            const selected = s === service;
            return (
              <button
                key={s}
                type="button"
                onClick={() => {
                  setService(s);
                }}
                aria-pressed={selected}
                className={cn(
                  "flex min-h-11 items-center gap-2 rounded-xl px-3 py-2 text-left text-[12px] font-semibold leading-snug transition-colors",
                  selected
                    ? "bg-primary-dark text-white"
                    : "bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark",
                )}
              >
                <Stethoscope size={14} className="shrink-0" aria-hidden="true" />
                <span className="min-w-0 break-words">{s === OTHER_SERVICE ? t("booking.otherService") : s}</span>
              </button>
            );
          })}
        </div>
      </fieldset>

      <fieldset className="mt-4 min-w-0">
        <legend className="mb-2 text-[12px] font-bold text-text-primary">{t("booking.dateLabel")}</legend>
        <div className="grid grid-cols-4 gap-2">
          {days.map((d) => {
            const selected = d.iso === date;
            return (
              <button
                key={d.iso}
                type="button"
                onClick={() => {
                  setDate(d.iso);
                  setValidation(null);
                }}
                aria-pressed={selected}
                className={cn(
                  "flex min-h-14 flex-col items-center justify-center rounded-xl px-1 py-1.5 transition-colors",
                  selected
                    ? "bg-primary-dark text-white"
                    : "bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark",
                )}
              >
                <span className="whitespace-nowrap text-[10px] font-semibold">{d.weekday}</span>
                <span className={cn("text-[14px] font-bold", selected ? "text-white" : "text-text-primary")}>
                  {d.day}
                </span>
              </button>
            );
          })}
        </div>
      </fieldset>

      <label className="block pt-4 text-[12px] font-bold text-text-primary">
        {t("booking.timeLabel")}
        <input
          type="time"
          step={900}
          value={time}
          onChange={(event) => {
            setTime(event.target.value);
            setValidation(null);
          }}
          className="mt-2 block min-h-11 w-full rounded-xl border border-border bg-background-alt px-3 text-[13px] font-normal text-text-primary outline-none focus:border-primary"
        />
      </label>

      <label className="block pt-4 text-[12px] font-bold text-text-primary">
        {t("booking.noteLabel")}
        <textarea
          rows={3}
          maxLength={NOTE_MAX}
          value={note}
          onChange={(event) => {
            setNote(event.target.value);
          }}
          placeholder={t("booking.notePlaceholder")}
          className="mt-2 block w-full resize-none rounded-xl border border-border bg-background-alt px-3 py-2.5 text-[12px] font-normal leading-relaxed text-text-primary outline-none placeholder:text-text-tertiary focus:border-primary"
        />
      </label>

      <p className="pt-3 text-[11px] leading-relaxed text-text-tertiary">{t("booking.requestDisclaimer")}</p>

      {validation ? (
        <p role="alert" className="pt-2 text-[12px] font-semibold text-danger-text">
          {t(validation)}
        </p>
      ) : null}

      <button
        type="submit"
        disabled={state === "submitting"}
        className="mt-3 flex min-h-12 w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 text-[14px] font-bold text-white shadow-brand-lg hover:bg-primary disabled:opacity-60"
      >
        <CalendarCheck size={16} aria-hidden="true" />
        {state === "submitting" ? t("booking.submitting") : t("booking.submit")}
      </button>

      {resultMessage ? (
        <p
          role="status"
          className={cn(
            "mt-3 flex items-start gap-2 rounded-xl px-3 py-2.5 text-[12px] leading-relaxed",
            state === "success" ? "bg-success-bg text-success-text" : "bg-danger-bg text-danger-text",
          )}
        >
          {state === "success" ? <CheckCircle2 size={15} className="mt-0.5 shrink-0" aria-hidden="true" /> : null}
          {resultMessage}
        </p>
      ) : null}

      {phone ? (
        <a
          href={telHref(phone)}
          className="mt-3 flex min-h-11 items-center justify-center gap-1.5 rounded-xl bg-chip-bg px-3 text-[12px] font-bold text-primary-dark hover:bg-info"
        >
          <Phone size={14} className="shrink-0" aria-hidden="true" />
          {t("booking.callClinic", { phone })}
        </a>
      ) : null}
    </form>
  );
}
