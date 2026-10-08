import { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useLocation, useParams } from "react-router";
import { CalendarCheck, ChevronRight, FileText, Info, MapPin, Navigation, Phone, Stethoscope } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { EmptyState, ErrorState, Sheet, SheetContent, SheetDescription, SheetTitle, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { getPlace, listPlaceReviews, placeErrorStatus, type PlaceApi } from "@/features/place";
import { BookingPanel } from "./ClinicBooking";
import { REVIEW_LIMIT, ReviewsSection } from "./ClinicReviews";
import { PlaceMap } from "./PlaceMap";
import { KindChip, PlaceBadges, RatingLine } from "./placeParts";
import { directionsUrl, telHref, toViewPlace } from "./placeView";

/**
 * `/map/clinics/:clinicId` — chi tiết một cơ sở.
 *
 * NGUỒN DỮ LIỆU: `GET /places/{id}`, `GET /places/{id}/reviews`, `POST .../reviews`,
 * `POST .../bookings`. KHÔNG có endpoint ảnh cơ sở, giờ mở cửa, bác sĩ, bảng giá, chứng nhận,
 * "khám từ xa" hay chia sẻ hồ sơ trực tiếp — các khối đó trong design đã bị bỏ. Vị trí ảnh
 * bìa trong design được thay bằng bản đồ nhỏ dựng từ toạ độ thật của cơ sở.
 *
 * `#booking` (từ nút "Đặt lịch" ở `/map`): mobile mở sẵn bottom sheet đặt lịch, desktop cuộn tới form.
 */

const BOOKING_FORM_ID = "clinic-booking-form";

function ContactButtons({ place, showMapLink }: { place: PlaceApi; showMapLink: boolean }) {
  const { t } = useTranslation("map");
  const phone = place.phone?.trim() ?? "";
  const directions = directionsUrl(place);
  const base =
    "flex min-h-11 flex-1 items-center justify-center gap-1.5 whitespace-nowrap rounded-xl px-3 text-[13px] font-bold transition-colors";
  return (
    <div className="flex gap-2">
      {phone ? (
        <a href={telHref(phone)} className={cn(base, "bg-primary-dark text-white hover:bg-primary")}>
          <Phone size={15} aria-hidden="true" />
          {t("actions.call")}
        </a>
      ) : null}
      {directions ? (
        <a
          href={directions}
          target="_blank"
          rel="noreferrer"
          className={cn(base, "bg-secondary text-secondary-text-on hover:bg-secondary-light")}
        >
          <Navigation size={15} aria-hidden="true" />
          {t("actions.directions")}
        </a>
      ) : null}
      {showMapLink ? (
        <Link
          to={`/map?place=${encodeURIComponent(place.id)}`}
          className={cn(base, "bg-chip-bg text-primary-dark hover:bg-info")}
        >
          <MapPin size={15} aria-hidden="true" />
          {t("actions.showOnMap")}
        </Link>
      ) : null}
    </div>
  );
}

function FactsList({ place }: { place: PlaceApi }) {
  const { t } = useTranslation("map");
  const phone = place.phone?.trim() ?? "";
  const rows = [
    { id: "address", icon: MapPin, label: t("clinic.addressLabel"), value: place.address, strong: false },
    { id: "area", icon: Navigation, label: t("clinic.areaLabel"), value: place.area, strong: false },
    ...(phone ? [{ id: "phone", icon: Phone, label: t("clinic.phoneLabel"), value: phone, strong: true }] : []),
  ];
  return (
    <dl className="grid grid-cols-[minmax(0,96px)_1fr] gap-x-3 gap-y-2.5 text-[13px]">
      {rows.map((row) => (
        <div key={row.id} className="contents">
          <dt className="flex items-start gap-1.5 text-text-tertiary">
            <row.icon size={14} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
            {row.label}
          </dt>
          <dd className={cn("min-w-0 break-words", row.strong ? "font-bold text-text-primary" : "text-text-secondary")}>
            {row.value}
          </dd>
        </div>
      ))}
    </dl>
  );
}

/** Chuyên khoa do cơ sở công bố (`specialties[]`) — dạng ô như khối "chuyên khoa" của design. */
function SpecialtyTiles({ specialties, className }: { specialties: string[]; className?: string }) {
  if (specialties.length === 0) return null;
  return (
    <ul className={cn("grid grid-cols-2 gap-2.5", className)}>
      {specialties.map((s) => (
        <li key={s} className="flex items-center gap-2.5 rounded-xl bg-surface p-3 shadow-xs">
          <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-chip-bg text-primary-dark">
            <Stethoscope size={16} aria-hidden="true" />
          </span>
          <span className="min-w-0 break-words text-[13px] font-semibold leading-snug text-text-primary">{s}</span>
        </li>
      ))}
    </ul>
  );
}

function DataNotice() {
  const { t } = useTranslation("map");
  return (
    <p className="flex items-start gap-2 rounded-xl bg-background-alt px-3.5 py-3 text-[11px] leading-relaxed text-text-secondary">
      <Info size={14} className="mt-0.5 shrink-0 text-text-tertiary" aria-hidden="true" />
      {t("clinic.dataNotice")}
    </p>
  );
}

function DetailSkeleton({ desktop }: { desktop: boolean }) {
  return (
    <div className={cn("flex gap-6", desktop ? "items-start" : "flex-col px-4 py-4")} aria-busy="true">
      <div className="flex min-w-0 flex-1 flex-col gap-4">
        <SkeletonLoader shape="card" className="h-56 rounded-2xl" />
        <SkeletonLoader shape="text" className="h-8 w-2/3" />
        <SkeletonLoader shape="text" className="w-1/2" />
        <SkeletonLoader shape="card" className="h-40 rounded-2xl" />
      </div>
      {desktop ? <SkeletonLoader shape="card" className="h-[560px] w-[360px] shrink-0 rounded-2xl" /> : null}
    </div>
  );
}

export function ClinicDetailPage() {
  const { t } = useTranslation("map");
  const isDesktop = useBreakpoint("lg");
  const { clinicId = "" } = useParams<{ clinicId: string }>();
  const { hash } = useLocation();
  const [bookingOpen, setBookingOpen] = useState(hash === "#booking");

  const placeQuery = useQuery({
    queryKey: ["place", clinicId],
    queryFn: ({ signal }) => getPlace(clinicId, signal),
    enabled: Boolean(clinicId),
    staleTime: 60_000,
    retry: (count, error) => placeErrorStatus(error) !== 404 && count < 2,
  });
  const reviewsQuery = useQuery({
    queryKey: ["place", clinicId, "reviews"],
    queryFn: ({ signal }) => listPlaceReviews(clinicId, REVIEW_LIMIT, signal),
    enabled: Boolean(clinicId) && placeQuery.isSuccess,
    staleTime: 30_000,
  });

  const loaded = placeQuery.isSuccess;
  useEffect(() => {
    if (!loaded || !isDesktop || hash !== "#booking") return;
    document.getElementById(BOOKING_FORM_ID)?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [loaded, isDesktop, hash]);

  if (placeQuery.isPending) return <DetailSkeleton desktop={isDesktop} />;

  if (placeQuery.isError) {
    const notFound = placeErrorStatus(placeQuery.error) === 404;
    const backLink = (
      <Link
        to="/map"
        className="inline-flex min-h-11 items-center gap-1.5 rounded-xl bg-chip-bg px-4 text-[13px] font-bold text-primary-dark hover:bg-info"
      >
        <MapPin size={14} aria-hidden="true" />
        {t("actions.backToMap")}
      </Link>
    );
    return (
      <div className={cn("rounded-2xl bg-surface shadow-brand-md", !isDesktop && "mx-4 my-4")}>
        {notFound ? (
          <EmptyState
            icon={<MapPin size={22} />}
            title={t("clinic.notFoundTitle")}
            description={t("clinic.notFound")}
            action={backLink}
          />
        ) : (
          <ErrorState
            title={t("clinic.loadErrorTitle")}
            description={t("clinic.loadError")}
            onRetry={() => {
              void placeQuery.refetch();
            }}
          />
        )}
      </div>
    );
  }

  const place = placeQuery.data;
  const view = toViewPlace(place);

  const reviews = (
    <ReviewsSection
      place={place}
      reviews={reviewsQuery.data}
      loading={reviewsQuery.isPending}
      failed={reviewsQuery.isError}
      onRetry={() => {
        void reviewsQuery.refetch();
      }}
      headingClassName={isDesktop ? "text-[18px] text-primary-dark" : "text-[17px] text-text-primary"}
    />
  );

  const titleBlock = (
    <>
      <div className="flex flex-wrap items-center gap-1.5">
        <KindChip kind={view.kind} size="md" />
        <PlaceBadges badges={place.badges} size="md" />
      </div>
      <h1
        className={cn(
          "pt-2.5 font-bold leading-tight",
          isDesktop ? "text-[30px] text-primary-dark" : "text-[22px] text-text-primary",
        )}
      >
        {place.name}
      </h1>
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-2">
        <RatingLine rating={place.rating} reviewCount={place.reviewCount} size="md" />
        <span className="text-[12px] text-text-secondary">{place.area}</span>
      </div>
    </>
  );

  if (!isDesktop) {
    return (
      <div className="flex flex-col gap-5 px-4 py-4">
        <Link to="/map" className="flex min-h-9 items-center gap-1.5 text-[12px] font-semibold text-primary-dark">
          <MapPin size={13} aria-hidden="true" />
          {t("actions.backToMap")}
        </Link>

        <div>{titleBlock}</div>

        <ContactButtons place={place} showMapLink={false} />

        <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
          <div className="flex items-center justify-between gap-3 pb-3">
            <h2 className="text-[17px] font-bold text-text-primary">{t("clinic.locationTitle")}</h2>
            <Link
              to={`/map?place=${encodeURIComponent(place.id)}`}
              className="flex min-h-9 items-center text-[12px] font-bold text-primary-dark hover:underline"
            >
              {t("actions.viewMap")}
            </Link>
          </div>
          <PlaceMap places={[view]} interactive={false} className="h-44 w-full rounded-xl" />
          <div className="pt-4">
            <FactsList place={place} />
          </div>
        </section>

        {place.specialties.length > 0 ? (
          <section>
            <h2 className="pb-3 text-[17px] font-bold text-text-primary">{t("clinic.specialtiesTitle")}</h2>
            <SpecialtyTiles specialties={place.specialties} />
          </section>
        ) : null}

        <section className="rounded-2xl bg-surface p-4 shadow-brand-md">{reviews}</section>

        <DataNotice />
        <DisclaimerBanner variant="short" />

        <div className="flex flex-col gap-2.5">
          <button
            type="button"
            onClick={() => {
              setBookingOpen(true);
            }}
            className="flex min-h-12 items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 text-[15px] font-bold text-white shadow-brand-lg hover:bg-primary"
          >
            <CalendarCheck size={17} aria-hidden="true" />
            {t("clinic.bookCta")}
          </button>
          <Link
            to="/export"
            className="flex min-h-12 items-center justify-center gap-2 rounded-xl bg-chip-bg px-4 text-[14px] font-bold text-primary-dark hover:bg-info"
          >
            <FileText size={16} aria-hidden="true" />
            {t("clinic.sharePdfCta")}
          </Link>
        </div>

        <Sheet open={bookingOpen} onOpenChange={setBookingOpen}>
          <SheetContent className="max-h-[90dvh] px-4 pb-8 pt-4">
            <SheetTitle className="text-[18px] font-bold text-primary-dark">{t("booking.title")}</SheetTitle>
            <SheetDescription className="pb-4 pt-1 text-[12px] leading-relaxed">
              {t("booking.sheetSubtitle", { name: place.name })}
            </SheetDescription>
            <BookingPanel place={place} formId={BOOKING_FORM_ID} variant="sheet" />
          </SheetContent>
        </Sheet>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <nav aria-label={t("clinic.breadcrumb")}>
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
          <li>{place.area}</li>
          <li aria-hidden="true">
            <ChevronRight size={13} />
          </li>
          <li className="font-semibold text-text-primary" aria-current="page">
            {place.name}
          </li>
        </ol>
      </nav>

      <div className="flex items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-5">
          <section className="overflow-hidden rounded-2xl bg-surface shadow-brand-md">
            <PlaceMap places={[view]} interactive={false} className="h-[260px] w-full rounded-none" />
            <div className="p-6">
              {titleBlock}
              {place.specialties.length > 0 ? (
                <div className="mt-5 rounded-2xl bg-background-alt p-4">
                  <h2 className="pb-3 text-[13px] font-bold text-text-primary">{t("clinic.specialtiesTitle")}</h2>
                  <SpecialtyTiles specialties={place.specialties} />
                </div>
              ) : null}
              <div className="pt-5">
                <FactsList place={place} />
              </div>
              <div className="pt-5">
                <ContactButtons place={place} showMapLink />
              </div>
            </div>
          </section>

          <section className="rounded-2xl bg-surface p-6 shadow-brand-md">{reviews}</section>

          <DataNotice />
          <DisclaimerBanner variant="short" />
        </div>

        <aside className="sticky top-4 flex w-[360px] shrink-0 flex-col gap-3">
          <BookingPanel place={place} formId={BOOKING_FORM_ID} variant="card" />
          <Link
            to="/export"
            className="flex min-h-11 items-center justify-center gap-2 rounded-xl bg-surface px-4 text-[13px] font-bold text-primary-dark shadow-xs hover:bg-chip-bg"
          >
            <FileText size={15} aria-hidden="true" />
            {t("clinic.sharePdfCta")}
          </Link>
        </aside>
      </div>
    </div>
  );
}
