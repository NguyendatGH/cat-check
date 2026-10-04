import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import {
  CalendarCheck,
  ChevronRight,
  Clock,
  Droplet,
  Droplets,
  FileText,
  FlaskConical,
  Heart,
  Images,
  MapPin,
  Navigation,
  PawPrint,
  Phone,
  Scan,
  Share2,
  ShieldCheck,
  Siren,
  Star,
  Stethoscope,
  VolumeX,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { createPlaceBooking, createPlaceReview, getPlace, listPlaceReviews, type PlaceReviewApi } from "@/features/place";
import {
  DESIGN_MOCK_CLINIC_DETAIL,
  DESIGN_MOCK_MAP_RASTER_WIDE,
  DESIGN_MOCK_PLACES,
  findMockPlace,
  formatVnd,
} from "./mockData";

/**
 * `/map/clinics/:clinicId` — Chi tiết Phòng khám Thú y.
 *
 * Mobile (< lg) theo Figma `1:2371`; desktop (>= lg) theo `16:2257` (cột nội dung + panel
 * đặt lịch 380px dính bên phải, tổng vừa khung 944px của `AppLayout` — KHÔNG tự thêm padding
 * ngang ở `lg`).
 *
 * Địa điểm, yêu cầu đặt lịch và đánh giá dùng API thật. Nội dung giờ mở cửa, bác sĩ, dịch vụ,
 * ảnh và các thẻ đánh giá đang có trong mock data vẫn là dữ liệu thiết kế, không phải dữ liệu
 * do cơ sở xác nhận.
 *
 * COPY: bỏ toàn bộ câu hứa phát hiện máu trong mockup (quyết định #8 của owner) và các từ
 * bị `REQ-COPY-01` chặn — chi tiết ở đầu `mockData.ts`.
 */

const SERVICE_ICON = {
  flask: FlaskConical,
  scan: Scan,
  paw: PawPrint,
  droplet: Droplet,
} as const;

const BOOKING_SERVICE_ICON = {
  droplet: Droplet,
  scan: Scan,
  stethoscope: Stethoscope,
  siren: Siren,
} as const;

const HIGHLIGHT_ICON = {
  kidney: Droplets,
  mute: VolumeX,
} as const;

const PRICE_BADGE_TONE = {
  success: "bg-success-bg text-success-text",
  info: "bg-chip-bg text-primary-dark",
  secondary: "bg-secondary text-secondary-text-on",
} as const;

function Stars({ value }: { value: number }) {
  return (
    <span className="flex items-center gap-0.5" aria-hidden="true">
      {[0, 1, 2, 3, 4].map((i) => (
        <Star
          key={i}
          size={11}
          className={i < Math.round(value) ? "text-secondary" : "text-border"}
          fill="currentColor"
        />
      ))}
    </span>
  );
}

function ReviewForm({ placeId }: { placeId: string }) {
  const { t } = useTranslation("map");
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(5);
  const [body, setBody] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<"success" | "error" | null>(null);

  async function submitReview(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setResult(null);
    try {
      await createPlaceReview(placeId, { rating, body: body.trim() || undefined });
      setResult("success");
      void queryClient.invalidateQueries({ queryKey: ["place", placeId, "reviews"] });
      void queryClient.invalidateQueries({ queryKey: ["place", placeId] });
    } catch {
      setResult("error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={(event) => { void submitReview(event); }} className="mt-4 rounded-xl bg-background-alt p-4">
      <p className="text-[13px] font-bold text-text-primary">{t("clinic.reviewFormTitle")}</p>
      <div className="flex gap-1 pt-2" role="group" aria-label={t("clinic.ratingLabel")}>
        {[1, 2, 3, 4, 5].map((value) => (
          <button key={value} type="button" aria-label={t("clinic.ratingValue", { value })} aria-pressed={rating === value} onClick={() => { setRating(value); }} className="rounded p-1 focus-visible:outline-2 focus-visible:outline-primary">
            <Star size={20} fill={value <= rating ? "currentColor" : "none"} className={value <= rating ? "text-secondary" : "text-border"} />
          </button>
        ))}
      </div>
      <label className="mt-2 block text-[12px] font-semibold text-text-secondary">
        {t("clinic.reviewBodyLabel")}
        <textarea value={body} maxLength={1000} rows={3} onChange={(event) => { setBody(event.target.value); }} placeholder={t("clinic.reviewBodyPlaceholder")} className="mt-1.5 w-full resize-y rounded-lg border border-border bg-surface px-3 py-2 text-[12px] font-normal text-text-primary" />
      </label>
      <button type="submit" disabled={submitting} className="mt-3 rounded-lg bg-primary-dark px-4 py-2.5 text-[12px] font-bold text-white disabled:opacity-60">
        {submitting ? t("clinic.reviewSubmitting") : t("clinic.reviewSubmit")}
      </button>
      {result ? <p role="status" className={cn("pt-2 text-[12px]", result === "success" ? "text-success-text" : "text-danger-text")}>{t(result === "success" ? "clinic.reviewSent" : "clinic.reviewError")}</p> : null}
    </form>
  );
}

function PlaceReviewList({ reviews, loading, failed }: { reviews: PlaceReviewApi[]; loading: boolean; failed: boolean }) {
  const { t, i18n } = useTranslation("map");
  if (loading) return <p className="py-3 text-[12px] text-text-tertiary">{t("clinic.reviewsLoading")}</p>;
  if (failed) return <p role="alert" className="py-3 text-[12px] text-danger-text">{t("clinic.reviewsLoadError")}</p>;
  if (reviews.length === 0) return <p className="py-3 text-[12px] text-text-tertiary">{t("clinic.reviewsEmpty")}</p>;

  return (
    <ul className="flex flex-col gap-2 pt-3">
      {reviews.map((review) => (
        <li key={review.id} className="rounded-xl bg-background-alt/70 p-3.5">
          <div className="flex items-center justify-between gap-3">
            <Stars value={review.rating} />
            <time className="text-[10px] text-text-tertiary" dateTime={review.createdAt}>
              {new Date(review.createdAt).toLocaleDateString(i18n.language)}
            </time>
          </div>
          {review.body ? <p className="pt-2 text-[12px] leading-relaxed text-text-secondary">{review.body}</p> : null}
          <p className="pt-2 text-[10px] text-text-tertiary">{t("clinic.reviewAnonymous")}</p>
        </li>
      ))}
    </ul>
  );
}

/* --------------------------------------------------------- panel đặt lịch */

function BookingPanel({ clinicPhone, formId }: { clinicPhone: string; formId: string }) {
  const { t } = useTranslation("map");
  const [serviceId, setServiceId] = useState("general");
  const [note, setNote] = useState("");
  const [bookingDate, setBookingDate] = useState("");
  const [bookingTime, setBookingTime] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [bookingResult, setBookingResult] = useState<"success" | "error" | null>(null);
  const placeId = useParams<{ clinicId: string }>().clinicId ?? "";

  async function submitBooking(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setBookingResult(null);
    try {
      await createPlaceBooking(placeId, {
        serviceCode: serviceId.toUpperCase(),
        date: bookingDate,
        timeSlot: bookingTime,
        note: note.trim() || undefined,
      });
      setBookingResult("success");
    } catch {
      setBookingResult("error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form id={formId} onSubmit={(event) => { void submitBooking(event); }} className="rounded-2xl bg-surface p-5 shadow-brand-lg">
      <div className="flex items-start justify-between gap-3">
        <h2 className="flex items-center gap-2 text-[15px] font-bold text-text-primary">
          <span className="size-2 shrink-0 rounded-full bg-success" aria-hidden="true" />
          {t("booking.title")}
        </h2>
        <span className="shrink-0 rounded-md bg-chip-bg px-2 py-0.5 text-[10px] font-bold text-primary-dark">
          {t("booking.liveSync")}
        </span>
      </div>
      <p className="pt-2 text-[12px] leading-relaxed text-text-secondary">{t("booking.subtitle")}</p>
      <p className="pt-2 text-[11px] leading-relaxed text-text-secondary">{t("booking.requestDisclaimer")}</p>

      {/* Dịch vụ ưu tiên */}
      <fieldset className="pt-4">
        <legend className="text-[12px] font-bold text-text-primary">{t("booking.serviceLabel")}</legend>
        <div className="grid grid-cols-2 gap-2 pt-2">
          {[
            { id: "urinary", label: t("booking.urinary"), icon: "droplet" as const },
            { id: "ultrasound", label: t("booking.ultrasound"), icon: "scan" as const },
            { id: "general", label: t("booking.general"), icon: "stethoscope" as const },
            { id: "emergency", label: t("booking.emergencyService"), icon: "siren" as const },
          ].map((s) => {
            const Icon = BOOKING_SERVICE_ICON[s.icon];
            const selected = s.id === serviceId;
            return (
              <button
                key={s.id}
                type="button"
                onClick={() => {
                  setServiceId(s.id);
                }}
                aria-pressed={selected}
                className={cn(
                  "flex items-center gap-2 rounded-xl px-2.5 py-2.5 text-left text-[11px] font-semibold transition-colors",
                  selected
                    ? "bg-primary-dark text-white"
                    : "bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark",
                )}
              >
                <Icon size={14} className="shrink-0" aria-hidden="true" />
                {s.label}
              </button>
            );
          })}
        </div>
      </fieldset>

      <div className="grid grid-cols-2 gap-3 pt-4">
        <label className="text-[12px] font-bold text-text-primary">
          {t("booking.dateLabel")}
          <input required type="date" min={new Date().toISOString().slice(0, 10)} value={bookingDate} onChange={(event) => { setBookingDate(event.target.value); }} className="mt-2 w-full rounded-xl border border-border bg-background-alt px-3 py-2.5 text-[12px] font-normal" />
        </label>
        <label className="text-[12px] font-bold text-text-primary">
          {t("booking.timeInputLabel")}
          <input required type="time" value={bookingTime} onChange={(event) => { setBookingTime(event.target.value); }} className="mt-2 w-full rounded-xl border border-border bg-background-alt px-3 py-2.5 text-[12px] font-normal" />
        </label>
      </div>

      {/* Ghi chú */}
      <div className="pt-4">
        <label htmlFor="booking-note" className="text-[12px] font-bold text-text-primary">
          {t("booking.noteLabel")}
        </label>
        <textarea
          id="booking-note"
          rows={4}
          value={note}
          onChange={(e) => {
            setNote(e.target.value);
          }}
          placeholder={t("booking.notePlaceholder")}
          className="mt-2 w-full resize-none rounded-xl border border-border bg-background-alt px-3 py-2.5 text-[12px] leading-relaxed text-text-secondary outline-none focus:border-primary"
        />
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[13px] font-bold text-white hover:bg-primary"
      >
        <CalendarCheck size={16} aria-hidden="true" />
        {submitting ? t("booking.submitting") : t("booking.submit")}
      </button>
      {bookingResult ? <p role="status" className={cn("pt-3 text-[12px]", bookingResult === "success" ? "text-success-text" : "text-danger-text")}>{t(bookingResult === "success" ? "booking.requestSent" : "booking.requestError")}</p> : null}

      {clinicPhone ? <div className="flex gap-2 pt-3">
        <a
          href={`tel:${clinicPhone.replace(/\s/g, "")}`}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-danger-bg px-3 py-2.5 text-[11px] font-bold text-danger-text hover:bg-danger-bg/70"
        >
          <Phone size={13} className="shrink-0" aria-hidden="true" />
          {t("booking.callClinic", { phone: clinicPhone })}
        </a>
      </div> : null}
    </form>
  );
}

/* -------------------------------------------------------------------- page */

export function ClinicDetailPage() {
  const { t } = useTranslation("map");
  const { clinicId } = useParams<{ clinicId: string }>();
  const placeQuery = useQuery({
    queryKey: ["place", clinicId],
    queryFn: () => getPlace(clinicId ?? ""),
    enabled: Boolean(clinicId),
    staleTime: 60_000,
  });
  const reviewsQuery = useQuery({
    queryKey: ["place", clinicId, "reviews"],
    queryFn: () => listPlaceReviews(clinicId ?? "", 20),
    enabled: Boolean(clinicId),
    staleTime: 30_000,
  });
  const localPlace = DESIGN_MOCK_PLACES.find((item) => item.id === clinicId) ?? findMockPlace(clinicId ?? "");
  const place = placeQuery.data
    ? {
        ...localPlace,
        id: placeQuery.data.id,
        name: placeQuery.data.name,
        shortName: placeQuery.data.name,
        address: placeQuery.data.address,
        area: placeQuery.data.area,
        phone: placeQuery.data.phone ?? "",
        specialties: placeQuery.data.specialties,
        badges: placeQuery.data.badges,
        rating: placeQuery.data.rating,
        reviewCount: placeQuery.data.reviewCount,
        distanceKm: undefined,
        visitCount: 0,
        featured: false,
        certification: placeQuery.data.badges.find((badge) => badge.toLowerCase().includes("isfm")),
        latitude: placeQuery.data.latitude,
        longitude: placeQuery.data.longitude,
      }
    : localPlace;
  const detail = DESIGN_MOCK_CLINIC_DETAIL;
  const [saved, setSaved] = useState(false);

  if (placeQuery.isPending) {
    return <p className="rounded-xl bg-surface p-5 text-caption text-text-secondary">{t("clinic.reviewsLoading")}</p>;
  }
  if (placeQuery.isError) {
    return <p role="alert" className="rounded-xl bg-danger-bg p-5 text-caption text-danger-text">{t("clinic.notFound")}</p>;
  }

  const telehealthCard = (
    <section className="rounded-2xl bg-chip-bg/50 p-4">
      <div className="flex items-start gap-3">
        <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary-dark text-white">
          <Share2 size={17} aria-hidden="true" />
        </span>
        <div className="min-w-0 flex-1">
          <p className="flex flex-wrap items-center gap-2">
            <span className="text-[10px] font-bold tracking-[0.4px] text-primary-dark">{t("telehealth.eyebrow")}</span>
            <span className="rounded-md bg-secondary px-1.5 py-0.5 text-[9px] font-bold text-secondary-text-on">
              {t("telehealth.instant")}
            </span>
          </p>
          <p className="pt-1.5 text-[16px] font-bold leading-snug text-text-primary">{t("telehealth.title")}</p>
          <p className="pt-1.5 text-[12px] leading-relaxed text-text-secondary">{t("telehealth.body")}</p>
        </div>
      </div>
      <div className="flex flex-col gap-2.5 pt-3.5 sm:flex-row sm:items-center sm:justify-between">
        <p className="flex items-center gap-1.5 text-[11px] font-semibold text-success-text">
          <ShieldCheck size={13} className="shrink-0" aria-hidden="true" />
          {t("telehealth.secure")}
        </p>
        <button
          type="button"
          className="flex items-center justify-center gap-1.5 rounded-xl bg-surface px-4 py-2.5 text-[12px] font-bold text-primary-dark shadow-xs hover:bg-background-alt"
        >
          {t("telehealth.cta")}
          <ChevronRight size={14} aria-hidden="true" />
        </button>
      </div>
    </section>
  );

  const servicesGrid = (
    <div className="grid grid-cols-2 gap-3">
      {detail.services.map((s) => {
        const Icon = SERVICE_ICON[s.icon];
        return (
          <article key={s.id} className="rounded-2xl bg-background-alt/70 p-3.5">
            <span className="flex size-9 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
              <Icon size={16} aria-hidden="true" />
            </span>
            <p className="pt-2.5 text-[13px] font-bold leading-snug text-text-primary">{s.title}</p>
            <p className="pt-1.5 text-[11px] leading-relaxed text-text-secondary">{s.body}</p>
          </article>
        );
      })}
    </div>
  );

  return (
    <>
      {/* ------------------------------------------------------- mobile */}
      <div className="flex flex-col gap-4 pb-6 lg:hidden">
        {/* Figma cho ảnh bìa tràn hết bề ngang khung máy, không bo góc, không lề. */}
        <div className="relative overflow-hidden">
          <img src={detail.photos[4].src} alt="" className="aspect-[390/224] w-full object-cover" />
          <span className="absolute bottom-3 left-3 flex max-w-[calc(100%-7.5rem)] items-start gap-1.5 rounded-xl bg-surface/95 px-2.5 py-1.5 text-[11px] font-semibold leading-snug text-primary-dark">
            <ShieldCheck size={13} className="mt-px shrink-0" aria-hidden="true" />
            {place.certification ?? detail.goldStandardBadge}
          </span>
          <span className="absolute bottom-3 right-3 flex items-center gap-1.5 rounded-xl bg-surface/95 px-2.5 py-1.5 text-[11px] font-semibold text-text-secondary">
            <Images size={13} className="shrink-0" aria-hidden="true" />
            {t("clinic.photoCount", { current: 1, total: detail.photoTotal })}
          </span>
        </div>

        <div className="px-4">
          <h1 className="text-[21px] font-bold leading-tight text-text-primary">{place.name}</h1>
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 pt-2 text-[12px] text-text-secondary">
            <span className="flex items-center gap-1 font-bold text-text-primary">
              <Star size={12} className="text-secondary" fill="currentColor" aria-hidden="true" />
              {place.rating.toFixed(1)}
            </span>
            <span>{t("list.reviewCount", { count: place.reviewCount })}</span>
            <span className="flex items-center gap-1 font-semibold text-primary-dark">
              {place.distanceKm !== undefined ? <><Navigation size={12} aria-hidden="true" />{t("list.distance", { km: place.distanceKm.toFixed(1) })}</> : null}
            </span>
          </p>
          <p className="pt-1 text-[12px] text-text-secondary">{place.area}</p>
        </div>

        <div className="mx-4 flex items-center gap-3 rounded-2xl bg-background-alt px-3.5 py-3">
          <span className="mt-1 size-2 shrink-0 self-start rounded-full bg-success" aria-hidden="true" />
          <div className="min-w-0 flex-1">
            <p className="text-[12px] font-semibold text-text-primary">{place.openLabel}</p>
            {place.hotlineLabel ? <p className="pt-0.5 text-[12px] text-text-secondary">{place.hotlineLabel}</p> : null}
          </div>
          <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-success-bg text-success-text">
            <Stethoscope size={16} aria-hidden="true" />
          </span>
        </div>

        <div className="flex gap-2 px-4">
          {place.phone ? <a
            href={`tel:${place.phone.replace(/\s/g, "")}`}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-3 py-2.5 text-[13px] font-bold text-white hover:bg-primary"
          >
            <Phone size={14} aria-hidden="true" />
            {t("actions.callShort")}
          </a> : null}
          <button
            type="button"
            className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-secondary px-3 py-2.5 text-[13px] font-bold text-secondary-text-on hover:bg-secondary-light"
          >
            <Navigation size={14} aria-hidden="true" />
            {t("actions.directions")}
          </button>
          <button
            type="button"
            onClick={() => {
              setSaved((v) => !v);
            }}
            aria-pressed={saved}
            className={cn(
              "flex flex-1 items-center justify-center gap-1.5 rounded-xl px-3 py-2.5 text-[13px] font-bold transition-colors",
              saved ? "bg-danger text-white" : "bg-background-alt text-text-secondary hover:bg-chip-bg",
            )}
          >
            <Heart size={14} fill={saved ? "currentColor" : "none"} aria-hidden="true" />
            {t("actions.save")}
          </button>
        </div>

        <div className="px-4">{telehealthCard}</div>

        {/* Vị trí & lịch làm việc */}
        <section className="px-4">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-[17px] font-bold text-text-primary">{t("clinic.locationTitle")}</h2>
            <Link to="/map" className="shrink-0 text-[12px] font-semibold text-primary-dark hover:underline">
              {t("clinic.viewOnMap")}
            </Link>
          </div>
          <div className="relative mt-3 overflow-hidden rounded-xl">
            <img
              src={DESIGN_MOCK_MAP_RASTER_WIDE}
              alt={t("map.alt")}
              className="aspect-[326/128] w-full object-cover"
            />
            <span className="absolute bottom-2 left-2 flex items-center gap-1.5 rounded-lg bg-surface/95 px-2 py-1 text-[10px] font-semibold text-text-primary">
              <MapPin size={11} className="shrink-0 text-primary-dark" aria-hidden="true" />
              {detail.mapAreaLabel}
            </span>
          </div>
          <p className="flex items-start gap-2 pt-3 text-[13px] font-semibold text-text-primary">
            <MapPin size={15} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
            {place.address}
          </p>
          <div className="mt-3 rounded-xl bg-background-alt px-3.5 py-3">
            <p className="flex flex-wrap items-center justify-between gap-2 text-[12px]">
              <span className="flex items-center gap-1.5 font-semibold text-text-secondary">
                <Clock size={13} className="shrink-0" aria-hidden="true" />
                {t("clinic.regularHours")}
              </span>
              <span className="font-bold text-text-primary">{detail.hoursShort}</span>
            </p>
            <p className="flex flex-wrap items-center justify-between gap-2 pt-2 text-[12px]">
              <span className="flex items-center gap-1.5 font-semibold text-danger-text">
                <Siren size={13} className="shrink-0" aria-hidden="true" />
                {t("clinic.emergencyHotline")}
              </span>
              <span className="font-bold text-danger-text">{detail.emergencyHours}</span>
            </p>
          </div>
        </section>

        {/* Chuyên khoa */}
        <section className="px-4">
          <div className="flex items-center justify-between gap-3 pb-3">
            <h2 className="text-[17px] font-bold text-text-primary">{t("clinic.servicesTitle")}</h2>
            <span className="shrink-0 text-[11px] font-semibold text-text-tertiary">{t("clinic.servicesBadge")}</span>
          </div>
          {servicesGrid}
        </section>

        {/* Đánh giá */}
        <section className="px-4">
          <div className="flex items-center justify-between gap-3 pb-3">
            <h2 className="text-[17px] font-bold text-text-primary">{t("clinic.reviewsTitle")}</h2>
          </div>
          <div className="flex items-center gap-2 rounded-xl bg-surface px-3 py-2.5">
            <Stars value={place.rating} />
            <span className="text-[12px] font-semibold text-text-secondary">{t("clinic.reviewSummary", { rating: place.rating.toFixed(1), count: place.reviewCount })}</span>
          </div>
          <PlaceReviewList reviews={reviewsQuery.data ?? []} loading={reviewsQuery.isPending} failed={reviewsQuery.isError} />
          <ReviewForm placeId={place.id} />
        </section>

        <div className="px-4">
          <DisclaimerBanner variant="short" />
        </div>

        <div className="flex flex-col gap-2.5 px-4">
          <button
            type="button"
            onClick={() => { document.getElementById("clinic-booking-form-mobile")?.scrollIntoView({ behavior: "smooth", block: "center" }); }}
            className="flex items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white hover:bg-primary"
          >
            <CalendarCheck size={16} aria-hidden="true" />
            {t("clinic.bookCta")}
          </button>
          <Link
            to="/export"
            className="flex items-center justify-center gap-2 rounded-xl bg-background-alt px-4 py-3.5 text-[14px] font-bold text-primary-dark hover:bg-chip-bg"
          >
            <FileText size={16} aria-hidden="true" />
            {t("clinic.sharePdfCta")}
          </Link>
        </div>
        <div className="px-4">
          <BookingPanel clinicPhone={place.phone} formId="clinic-booking-form-mobile" />
        </div>
      </div>

      {/* ------------------------------------------------------ desktop */}
      <div className="hidden flex-col gap-5 lg:flex">
        <nav aria-label={t("clinic.locationTitle")}>
          <ol className="flex flex-wrap items-center gap-1.5 text-[12px] text-text-secondary">
            <li>
              <Link to="/map" className="flex items-center gap-1.5 font-semibold text-primary-dark hover:underline">
                <MapPin size={13} aria-hidden="true" />
                {t("actions.backToMap")}
              </Link>
            </li>
            <li aria-hidden="true">
              <ChevronRight size={13} />
            </li>
            <li>{detail.breadcrumbArea}</li>
            <li aria-hidden="true">
              <ChevronRight size={13} />
            </li>
            <li className="font-semibold text-text-primary">{place.name}</li>
          </ol>
        </nav>


        <div className="flex items-start gap-6">
          {/* Cột nội dung */}
          <div className="flex min-w-0 flex-1 flex-col gap-5">
            <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
              <div className="flex gap-3">
                <div className="relative min-w-0 flex-[2] overflow-hidden rounded-xl">
                  <img src={detail.photos[0].src} alt="" className="aspect-[306/245] w-full object-cover" />
                  <span className="absolute bottom-3 left-3 flex items-center gap-1.5 rounded-lg bg-surface/95 px-2.5 py-1.5 text-[11px] font-bold text-secondary-text-on">
                    <ShieldCheck size={13} className="shrink-0" aria-hidden="true" />
                    {detail.goldStandardBadge}
                  </span>
                </div>
                <div className="flex min-w-0 flex-1 flex-col gap-3">
                  <div className="relative min-h-0 flex-1 overflow-hidden rounded-xl">
                    <img src={detail.photos[1].src} alt="" className="size-full object-cover" />
                    <span className="absolute left-2 top-2 rounded-md bg-surface/95 px-2 py-0.5 text-[10px] font-semibold text-text-primary">
                      {detail.photos[1].caption}
                    </span>
                  </div>
                  <div className="relative min-h-0 flex-1 overflow-hidden rounded-xl">
                    <img src={detail.photos[2].src} alt="" className="size-full object-cover" />
                    <span className="absolute inset-0 flex items-center justify-center gap-1.5 bg-text-primary/50 text-[11px] font-bold text-white">
                      <Images size={13} aria-hidden="true" />
                      {t("clinic.moreAngles", { count: detail.moreAngles })}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex flex-wrap gap-2 pt-4">
                <span className="flex items-center gap-1.5 rounded-full bg-success-bg px-2.5 py-1 text-[11px] font-bold text-success-text">
                  <span className="size-1.5 rounded-full bg-success" aria-hidden="true" />
                  {detail.statusBadges[0]}
                </span>
                <span className="flex items-center gap-1.5 rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-bold text-primary-dark">
                  <FlaskConical size={11} aria-hidden="true" />
                  {detail.statusBadges[1]}
                </span>
              </div>

              <h1 className="max-w-[420px] pt-3 text-[30px] font-bold leading-tight text-primary-dark">{place.name}</h1>

              <div className="flex flex-wrap items-center gap-x-4 gap-y-2 pt-3">
                <p className="flex items-center gap-1.5 text-[12px] text-text-secondary">
                  <Star size={14} className="text-secondary" fill="currentColor" aria-hidden="true" />
                  <span className="text-[15px] font-bold text-text-primary">{place.rating.toFixed(1)}</span>
                  {t("list.reviewCountSen", { count: place.reviewCount })}
                </p>
                <p className="flex items-center gap-1.5 text-[12px] font-semibold text-primary-dark">
                  <Navigation size={13} aria-hidden="true" />
                  {place.distanceKm !== undefined ? t("list.distanceFromYou", { km: place.distanceKm.toFixed(1) }) : place.area}
                </p>
                <button
                  type="button"
                  aria-label={t("actions.share")}
                  className="flex size-8 items-center justify-center rounded-lg bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark"
                >
                  <Share2 size={14} aria-hidden="true" />
                </button>
              </div>

              <div className="grid grid-cols-2 gap-3 pt-4">
                {detail.highlights.map((h) => {
                  const Icon = HIGHLIGHT_ICON[h.icon];
                  return (
                    <div key={h.id} className="flex gap-2.5 rounded-xl bg-background-alt/70 p-3">
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
                        <Icon size={15} aria-hidden="true" />
                      </span>
                      <span className="min-w-0">
                        <span className="block text-[12px] font-bold leading-snug text-text-primary">{h.title}</span>
                        <span className="block pt-1 text-[11px] leading-relaxed text-text-secondary">{h.body}</span>
                      </span>
                    </div>
                  );
                })}
              </div>

              <dl className="grid grid-cols-[88px_1fr] gap-x-3 gap-y-2 pt-4 text-[12px]">
                <dt className="flex items-center gap-1.5 text-text-tertiary">
                  <MapPin size={13} className="shrink-0" aria-hidden="true" />
                  {t("clinic.addressLabel")}
                </dt>
                <dd className="text-text-secondary">{place.address}</dd>
                <dt className="flex items-center gap-1.5 text-text-tertiary">
                  <Clock size={13} className="shrink-0" aria-hidden="true" />
                  {t("clinic.hoursLabel")}
                </dt>
                <dd className="text-text-secondary">{detail.hours}</dd>
              </dl>
            </section>

            {/* Đội ngũ bác sĩ */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-center justify-between gap-3">
                <h2 className="flex items-center gap-2 text-[18px] font-bold text-primary-dark">
                  <Stethoscope size={18} aria-hidden="true" />
                  {t("clinic.doctorsTitle")}
                </h2>
                <span className="shrink-0 rounded-md bg-background-alt px-2 py-1 text-[10px] font-bold text-text-secondary">
                  {t("clinic.doctorsBadge")}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-3 pt-4">
                {detail.doctors.map((d) => (
                  <article key={d.id} className="rounded-xl bg-background-alt/70 p-3.5">
                    <div className="flex items-start gap-3">
                      <img src={d.photo} alt="" className="size-12 shrink-0 rounded-xl object-cover" />
                      <div className="min-w-0 flex-1">
                        <p className="text-[14px] font-bold leading-snug text-text-primary">{d.name}</p>
                        <p className="pt-0.5 text-[11px] text-text-secondary">{d.role}</p>
                      </div>
                    </div>
                    <p className="pt-2.5 text-[11px] leading-relaxed text-text-secondary">{d.bio}</p>
                    <div className="flex flex-wrap gap-1.5 pt-2.5">
                      {d.tags.map((tag) => (
                        <span
                          key={tag}
                          className={cn(
                            "rounded-md px-2 py-1 text-[10px] font-semibold",
                            d.isAdvisor ? "bg-chip-bg text-primary-dark" : "bg-surface text-text-secondary",
                          )}
                        >
                          {tag}
                        </span>
                      ))}
                    </div>
                  </article>
                ))}
              </div>
            </section>

            {/* Chuyên khoa */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-center justify-between gap-3 pb-4">
                <h2 className="text-[18px] font-bold text-primary-dark">{t("clinic.servicesTitle")}</h2>
                <span className="shrink-0 text-[11px] font-semibold text-text-tertiary">
                  {t("clinic.servicesBadge")}
                </span>
              </div>
              {servicesGrid}
            </section>

            {/* Bảng giá */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-start justify-between gap-4">
                <div className="min-w-0">
                  <h2 className="text-[18px] font-bold text-primary-dark">{t("clinic.pricingTitle")}</h2>
                  <p className="pt-1.5 text-[12px] text-text-secondary">{t("clinic.pricingSubtitle")}</p>
                </div>
                <span className="shrink-0 rounded-full bg-secondary/25 px-3 py-1.5 text-[11px] font-bold text-secondary-text-on">
                  {t("clinic.pricingBadge", { percent: detail.priceSavingPercent })}
                </span>
              </div>
              <div className="flex flex-col gap-3 pt-4">
                {detail.prices.map((p) => (
                  <article
                    key={p.id}
                    className={cn(
                      "flex items-start gap-4 rounded-xl p-3.5",
                      p.price === 0 ? "bg-secondary/15" : "bg-background-alt/70",
                    )}
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-[14px] font-bold leading-snug text-text-primary">{p.title}</p>
                      <p className="pt-1.5 text-[11px] leading-relaxed text-text-secondary">{p.body}</p>
                    </div>
                    <span
                      className={cn(
                        "shrink-0 rounded-md px-2 py-1 text-center text-[10px] font-bold leading-tight",
                        PRICE_BADGE_TONE[p.badgeTone],
                      )}
                    >
                      {p.badge}
                    </span>
                    <span className="w-[92px] shrink-0 text-right">
                      <span className="block text-[17px] font-bold leading-tight text-primary-dark">
                        {formatVnd(p.price)}
                      </span>
                      <s className="block pt-0.5 text-[11px] text-text-tertiary">{formatVnd(p.compareAtPrice)}</s>
                    </span>
                  </article>
                ))}
              </div>
              <p className="pt-3 text-[11px] text-text-tertiary">{t("clinic.pricingNotice")}</p>
            </section>

            {/* Đánh giá */}
            <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
              <div className="flex items-center justify-between gap-3 pb-4">
                <h2 className="flex items-center gap-2 text-[18px] font-bold text-primary-dark">
                  <Star size={17} fill="currentColor" className="text-secondary" aria-hidden="true" />
                  {t("clinic.reviewsTitleWeb")}
                </h2>
              </div>
              <div className="flex items-center gap-2 rounded-xl bg-background-alt px-3 py-2.5">
                <Stars value={place.rating} />
                <span className="text-[12px] font-semibold text-text-secondary">{t("clinic.reviewSummary", { rating: place.rating.toFixed(1), count: place.reviewCount })}</span>
              </div>
              <PlaceReviewList reviews={reviewsQuery.data ?? []} loading={reviewsQuery.isPending} failed={reviewsQuery.isError} />
              <ReviewForm placeId={place.id} />
            </section>

            {telehealthCard}

            <DisclaimerBanner variant="short" />
          </div>

          {/* Panel đặt lịch dính bên phải */}
          <div className="sticky top-4 w-[380px] shrink-0">
            <BookingPanel clinicPhone={place.phone} formId="clinic-booking-form-desktop" />
          </div>
        </div>
      </div>
    </>
  );
}
