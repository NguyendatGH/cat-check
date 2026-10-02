import { useState, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  AlertTriangle,
  BadgeCheck,
  Clock,
  FlaskConical,
  Heart,
  HelpCircle,
  Layers,
  Map as MapIcon,
  MapPin,
  Minus,
  Navigation,
  PawPrint,
  Phone,
  Plus,
  Crosshair,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Star,
  Stethoscope,
  Truck,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { DisclaimerBanner } from "@/entities/disclaimer";
import {
  DESIGN_MOCK_AREA,
  DESIGN_MOCK_CERTIFIED_COUNT,
  DESIGN_MOCK_FILTERS,
  DESIGN_MOCK_MAP_RASTER_CITY,
  DESIGN_MOCK_MAP_RASTER_WIDE,
  DESIGN_MOCK_NEARBY_COUNT,
  DESIGN_MOCK_PARTNER_COUNT,
  DESIGN_MOCK_PLACES,
  DESIGN_MOCK_QUICK_SERVICES,
  DESIGN_MOCK_CLINIC_DETAIL,
  formatVnd,
  type MockFilterChip,
  type MockPlace,
  type MockPinTone,
} from "./mockData";

/**
 * `/map` — Bản đồ Chăm sóc Mèo.
 *
 * Mobile (< lg) dựng theo Figma `1:2904`; desktop (>= lg) theo `16:5393` (split 376px danh
 * sách + 541px bản đồ, vừa đúng khung nội dung 944px mà `AppLayout` cấp — KHÔNG tự thêm
 * padding ngang ở `lg`).
 *
 * BẢN ĐỒ: app không có thư viện bản đồ và không gọi dịch vụ tile nào. Khung bản đồ ở đây là
 * ẢNH TĨNH tách từ file SVG thiết kế, phía trên phủ các marker định vị tuyệt đối theo phần
 * trăm (`MockPlace.pin`). Nút zoom phóng to/thu nhỏ chính ảnh đó, nút "lớp bản đồ" đổi giữa
 * hai ảnh nền, nút "về giữa" đưa mức phóng về 1 — không nút nào gọi mạng.
 *
 * RIÊNG TƯ (p4, mục đích `LOCATION_MAP`): màn này KHÔNG gọi `navigator.geolocation`. Khu vực
 * là hằng số mẫu người dùng tự chọn; khoảng cách là số dựng sẵn trong `mockData.ts`.
 */

const ZOOM_STEPS = [1, 1.35, 1.8] as const;

const PIN_TONE_CLASS: Record<MockPinTone, string> = {
  featured: "bg-primary-dark text-white ring-4 ring-secondary",
  emergency: "bg-danger text-white",
  clinic: "bg-primary-dark text-white",
  muted: "bg-on-primary-subtle text-primary-dark",
};

const KIND_ICON = {
  clinic: PawPrint,
  emergency: AlertTriangle,
  lab: FlaskConical,
} as const;

const QUICK_SERVICE_ICON = {
  decode: FlaskConical,
  hotline: HelpCircle,
  courier: Truck,
  alert: MapPin,
} as const;

const QUICK_SERVICE_TONE = {
  secondary: "bg-secondary/25 text-secondary-text-on",
  primary: "bg-chip-bg text-primary-dark",
  success: "bg-success-bg text-success-text",
  danger: "bg-danger-bg text-danger-text",
} as const;

interface MapCanvasProps {
  places: MockPlace[];
  activeId: string;
  onSelect: (id: string) => void;
  className?: string;
  children?: ReactNode;
}

/** Khung bản đồ tĩnh + marker. Dùng chung cho cả bản mobile và bản desktop. */
function MapCanvas({ places, activeId, onSelect, className, children }: MapCanvasProps) {
  const { t } = useTranslation("map");
  const [zoomIndex, setZoomIndex] = useState(0);
  const [wideLayer, setWideLayer] = useState(true);
  const zoom = ZOOM_STEPS[zoomIndex];

  const controlClass =
    "flex size-9 items-center justify-center bg-surface text-text-secondary transition-colors hover:bg-chip-bg hover:text-primary-dark";

  return (
    <div className={cn("relative overflow-hidden rounded-2xl bg-background-alt", className)}>
      <div
        className="absolute inset-0 origin-center transition-transform duration-300"
        style={{ transform: `scale(${zoom.toString()})` }}
      >
        <img
          src={wideLayer ? DESIGN_MOCK_MAP_RASTER_WIDE : DESIGN_MOCK_MAP_RASTER_CITY}
          alt={t("map.alt")}
          className="size-full object-cover"
        />
        <span className="pointer-events-none absolute inset-0 bg-primary-dark/5" aria-hidden="true" />

        {places.map((place) => {
          const Icon = KIND_ICON[place.kind];
          const active = place.id === activeId;
          return (
            <button
              key={place.id}
              type="button"
              onClick={() => { onSelect(place.id); }}
              aria-label={t("map.pinLabel", { name: place.name })}
              aria-pressed={active}
              className="absolute -translate-x-1/2 -translate-y-1/2"
              style={{ left: `${place.pin.leftPct.toString()}%`, top: `${place.pin.topPct.toString()}%` }}
            >
              <span
                className="flex flex-col items-center gap-1"
                style={{ transform: `scale(${(1 / zoom).toString()})` }}
              >
                {place.pin.label ? (
                  <span
                    className={cn(
                      "whitespace-nowrap rounded-md px-1.5 py-0.5 text-[10px] font-bold shadow-xs",
                      active ? "bg-secondary text-secondary-text-on" : "bg-surface/95 text-text-primary",
                    )}
                  >
                    {place.pin.label}
                  </span>
                ) : null}
                <span
                  className={cn(
                    "flex items-center justify-center rounded-xl shadow-brand-md transition-transform",
                    PIN_TONE_CLASS[place.pin.tone],
                    active ? "size-10 rounded-full" : "size-8",
                  )}
                >
                  <Icon size={active ? 18 : 15} aria-hidden="true" />
                </span>
              </span>
            </button>
          );
        })}
      </div>

      {/* Cụm điều khiển — thao tác hoàn toàn cục bộ trên ảnh tĩnh. */}
      <div className="absolute right-3 top-3 flex flex-col gap-2">
        <button
          type="button"
          onClick={() => { setWideLayer((v) => !v); }}
          aria-label={t("map.layers")}
          className={cn(controlClass, "rounded-xl shadow-brand-md")}
        >
          <Layers size={16} aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={() => { setZoomIndex(0); }}
          aria-label={t("map.recenter")}
          className={cn(controlClass, "rounded-xl shadow-brand-md")}
        >
          <Crosshair size={16} aria-hidden="true" />
        </button>
        <div className="overflow-hidden rounded-xl shadow-brand-md">
          <button
            type="button"
            onClick={() => { setZoomIndex((i) => Math.min(i + 1, ZOOM_STEPS.length - 1)); }}
            aria-label={t("map.zoomIn")}
            className={cn(controlClass, "border-b border-background-alt")}
          >
            <Plus size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => { setZoomIndex((i) => Math.max(i - 1, 0)); }}
            aria-label={t("map.zoomOut")}
            className={controlClass}
          >
            <Minus size={16} aria-hidden="true" />
          </button>
        </div>
      </div>

      {children}
    </div>
  );
}

function FilterChips({
  filters,
  active,
  onChange,
  className,
}: {
  filters: MockFilterChip[];
  active: MockFilterChip["id"];
  onChange: (id: MockFilterChip["id"]) => void;
  className?: string;
}) {
  const { t } = useTranslation("map");
  return (
    <div className={cn("flex gap-2", className)}>
      {filters.map((f) => (
        <button
          key={f.id}
          type="button"
          onClick={() => { onChange(f.id); }}
          className={cn(
            "flex shrink-0 items-center gap-1.5 rounded-full px-3 py-1.5 text-[12px] font-semibold transition-colors",
            active === f.id
              ? "bg-primary-dark text-white"
              : "bg-surface text-text-secondary hover:bg-chip-bg hover:text-primary-dark",
          )}
        >
          {t(`filters.${f.id}`)}
          <span
            className={cn(
              "rounded-full px-1.5 text-[10px] font-bold",
              active === f.id ? "bg-white/20" : "bg-background-alt text-text-tertiary",
            )}
          >
            {f.count}
          </span>
        </button>
      ))}
    </div>
  );
}

function matchesFilter(place: MockPlace, filter: MockFilterChip["id"]): boolean {
  if (filter === "all") return true;
  if (filter === "isfm") return place.badges.some((b) => b.includes("ISFM"));
  if (filter === "lab") return place.kind === "lab" || place.badges.some((b) => b.includes("CATCHECK"));
  return place.kind === filter;
}

/* ------------------------------------------------------------------ mobile */

function MobileFeaturedCard({ place }: { place: MockPlace }) {
  const { t } = useTranslation("map");
  const [saved, setSaved] = useState(false);

  return (
    <article className="rounded-2xl bg-surface p-4 shadow-brand-md">
      <div className="flex items-start justify-between gap-3">
        <span className="flex items-center gap-1.5 rounded-full bg-chip-bg px-2.5 py-1 text-[10px] font-bold tracking-[0.4px] text-primary-dark">
          <Star size={11} fill="currentColor" aria-hidden="true" />
          {t("list.featuredBadge")}
        </span>
        <button
          type="button"
          onClick={() => { setSaved((v) => !v); }}
          aria-label={saved ? t("list.saved") : t("list.save")}
          aria-pressed={saved}
          className={cn(
            "flex size-9 shrink-0 items-center justify-center rounded-full transition-colors",
            saved ? "bg-danger text-white" : "bg-danger-bg text-danger-text",
          )}
        >
          <Heart size={16} fill={saved ? "currentColor" : "none"} aria-hidden="true" />
        </button>
      </div>

      <Link
        to={`/map/clinics/${place.id}`}
        className="block pt-2.5 text-[20px] font-bold leading-tight text-text-primary hover:text-primary-dark"
      >
        {place.name}
      </Link>

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 pt-2 text-[12px] text-text-secondary">
        <span className="flex items-center gap-1 font-bold text-text-primary">
          <Star size={12} className="text-secondary" fill="currentColor" aria-hidden="true" />
          {place.rating.toFixed(1)}
        </span>
        <span>{t("list.reviewCount", { count: place.reviewCount })}</span>
        <span className="flex items-center gap-1 font-semibold text-primary-dark">
          <Navigation size={12} aria-hidden="true" />
          {t("list.distance", { km: place.distanceKm.toFixed(1) })}
        </span>
      </p>
      <p className="pt-1 text-[12px] text-text-secondary">{place.area}</p>

      <p className="mt-3 flex items-center gap-1.5 rounded-lg bg-success-bg px-2.5 py-1.5 text-[11px] font-semibold text-success-text">
        <BadgeCheck size={13} aria-hidden="true" />
        {place.openLabel}
      </p>
      {place.hotlineLabel ? (
        <p className="mt-1.5 flex items-center gap-1.5 rounded-lg bg-danger-bg px-2.5 py-1.5 text-[11px] font-semibold text-danger-text">
          <Phone size={13} aria-hidden="true" />
          {place.hotlineLabel}
        </p>
      ) : null}

      <p className="pt-3 text-[11px] font-semibold text-text-tertiary">{t("list.specialtiesLabel")}</p>
      <div className="flex flex-wrap gap-1.5 pt-1.5">
        {place.specialties.map((s) => (
          <span key={s} className="rounded-lg bg-background-alt px-2 py-1 text-[11px] text-text-secondary">
            {s}
          </span>
        ))}
      </div>

      {place.certification ? (
        <p className="mt-2.5 flex items-start gap-1.5 rounded-lg bg-secondary-light px-2.5 py-2 text-[11px] font-semibold text-secondary-text-on">
          <ShieldCheck size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
          {place.certification}
        </p>
      ) : null}

      <div className="flex gap-2 pt-4">
        <a
          href={`tel:${place.phone.replace(/\s/g, "")}`}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-3 py-2.5 text-[13px] font-bold text-white hover:bg-primary"
        >
          <Phone size={14} aria-hidden="true" />
          {t("actions.call")}
        </a>
        <Link
          to={`/map/clinics/${place.id}`}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-secondary px-3 py-2.5 text-[13px] font-bold text-secondary-text-on hover:bg-secondary-light"
        >
          <Navigation size={14} aria-hidden="true" />
          {t("actions.directions")}
        </Link>
      </div>
    </article>
  );
}

function MobileNearbyRow({ place }: { place: MockPlace }) {
  const { t } = useTranslation("map");
  const Icon = KIND_ICON[place.kind];
  return (
    <Link
      to={`/map/clinics/${place.id}`}
      className="flex items-center gap-3 rounded-2xl bg-surface p-3 shadow-xs hover:bg-background-alt"
    >
      <span
        className={cn(
          "flex size-10 shrink-0 items-center justify-center rounded-xl",
          place.kind === "emergency" ? "bg-danger-bg text-danger-text" : "bg-chip-bg text-primary-dark",
        )}
      >
        <Icon size={18} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block truncate text-[14px] font-bold text-text-primary">{place.shortName}</span>
        <span className="flex flex-wrap items-center gap-x-2 pt-0.5 text-[11px] text-text-secondary">
          <span className="flex items-center gap-1 font-semibold text-text-primary">
            <Star size={10} className="text-secondary" fill="currentColor" aria-hidden="true" />
            {place.rating.toFixed(1)}
          </span>
          <span>{t("list.distance", { km: place.distanceKm.toFixed(1) })}</span>
          <span className={place.openTone === "danger" ? "font-semibold text-danger-text" : "font-semibold text-success-text"}>
            {place.openLabel}
          </span>
        </span>
      </span>
      <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-background-alt text-primary-dark">
        <Navigation size={15} aria-hidden="true" />
      </span>
    </Link>
  );
}

/* ----------------------------------------------------------------- desktop */

function DesktopPlaceCard({ place, active, onSelect }: { place: MockPlace; active: boolean; onSelect: () => void }) {
  const { t } = useTranslation("map");
  return (
    <article
      className={cn(
        "relative overflow-hidden rounded-2xl bg-surface p-4 shadow-brand-md transition-shadow",
        active ? "ring-2 ring-primary-dark" : "hover:shadow-brand-lg",
      )}
    >
      {place.featured ? (
        <span className="absolute inset-y-0 left-0 w-1 bg-primary-dark" aria-hidden="true" />
      ) : null}

      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-wrap gap-1.5">
          {place.badges.map((b) => (
            <span
              key={b}
              className={cn(
                "rounded-md px-2 py-0.5 text-[10px] font-bold",
                b.includes("CATCHECK")
                  ? "bg-success-bg text-success-text"
                  : b.includes("24/7")
                    ? "bg-danger-bg text-danger-text"
                    : "bg-secondary/25 text-secondary-text-on",
              )}
            >
              {b}
            </span>
          ))}
        </div>
        <span className="shrink-0 rounded-lg bg-background-alt px-2 py-1 text-center text-[11px] font-bold leading-tight text-text-secondary">
          {place.distanceKm.toFixed(1)}
          <span className="block text-[10px] font-semibold text-text-tertiary">{t("list.kmUnit")}</span>
        </span>
      </div>

      <Link
        to={`/map/clinics/${place.id}`}
        onClick={onSelect}
        className="flex items-center gap-1.5 pt-2.5 text-[17px] font-bold leading-snug text-text-primary hover:text-primary-dark"
      >
        {place.shortName}
        <BadgeCheck size={15} className="shrink-0 text-primary" aria-hidden="true" />
      </Link>
      <p className="pt-1 text-[11px] text-text-tertiary">{place.address}</p>

      <div className="mt-3 grid grid-cols-2 gap-2 rounded-xl bg-background-alt/70 p-2.5">
        <p className="flex items-center gap-2 text-[11px] text-text-secondary">
          <span className="flex items-center gap-1 rounded-md bg-surface px-1.5 py-0.5 text-[11px] font-bold text-text-primary">
            <Star size={10} className="text-secondary" fill="currentColor" aria-hidden="true" />
            {place.rating.toFixed(1)}
          </span>
          <span className="min-w-0">
            <span className="block">{t("list.ratingLabel")}</span>
            <span className="block font-bold text-text-primary">
              {t("list.visitCount", { count: place.visitCount })}
            </span>
          </span>
        </p>
        <p className="flex items-center gap-2 text-[11px] text-text-secondary">
          <Stethoscope size={14} className="shrink-0 text-primary-dark" aria-hidden="true" />
          <span className="min-w-0">
            <span className="block">{t("list.leadDoctorLabel")}</span>
            <span className="block truncate font-bold text-text-primary">{place.leadDoctor}</span>
          </span>
        </p>
      </div>

      <p className="flex items-center gap-1.5 pt-2.5 text-[12px] text-text-secondary">
        <BadgeCheck size={13} className="shrink-0 text-success" aria-hidden="true" />
        {place.amenity}
      </p>
      <p className="pt-2 text-[12px] leading-relaxed text-text-secondary">{place.summary}</p>

      <p className="flex flex-wrap items-center gap-x-2 gap-y-1 pt-2.5 text-[11px] text-text-secondary">
        <span className="flex items-center gap-1 font-bold text-text-primary">
          <Star size={11} className="text-secondary" fill="currentColor" aria-hidden="true" />
          {place.rating.toFixed(1)}
        </span>
        <span>{t("list.reviewCount", { count: place.reviewCount })}</span>
        <span className="flex items-center gap-1">
          <Clock size={11} aria-hidden="true" />
          <span className={place.openTone === "danger" ? "font-semibold text-danger-text" : "font-semibold text-success-text"}>
            {place.openLabel}
          </span>
        </span>
      </p>

      <div className="flex gap-2 pt-3">
        <Link
          to={`/map/clinics/${place.id}`}
          onClick={onSelect}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-3 py-2.5 text-[12px] font-bold text-white hover:bg-primary"
        >
          <MapPin size={13} aria-hidden="true" />
          {t("actions.book")}
        </Link>
        <a
          href={`tel:${place.phone.replace(/\s/g, "")}`}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-danger-bg px-3 py-2.5 text-center text-[12px] font-bold leading-tight text-danger-text hover:bg-danger-bg/70"
        >
          <Phone size={13} className="shrink-0" aria-hidden="true" />
          {t("actions.urgentAdvice")}
        </a>
        <button
          type="button"
          onClick={onSelect}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-chip-bg px-3 py-2.5 text-[12px] font-bold text-primary-dark hover:bg-info"
        >
          <Navigation size={13} aria-hidden="true" />
          {t("actions.directions")}
        </button>
      </div>
    </article>
  );
}

/** Popup chi tiết nổi trên bản đồ (bản desktop, Figma `16:5393`). */
function DesktopMapPopup({ place }: { place: MockPlace }) {
  const { t } = useTranslation("map");
  const detail = DESIGN_MOCK_CLINIC_DETAIL;
  const price = detail.prices[0];
  const doctor = detail.doctors[0];

  return (
    <div className="absolute inset-x-4 bottom-4 flex gap-4 rounded-2xl bg-surface/95 p-4 shadow-brand-lg backdrop-blur-sm">
      <div className="relative w-[148px] shrink-0 overflow-hidden rounded-xl">
        <img src={detail.photos[3].src} alt="" className="size-full object-cover" />
        <span className="absolute left-2 top-2 rounded-md bg-surface/95 px-1.5 py-0.5 text-[10px] font-bold text-primary-dark">
          {detail.popupBadge}
        </span>
        <span className="absolute inset-x-2 bottom-2 rounded bg-text-primary/80 px-2 py-1 text-[10px] font-semibold text-white">
          {place.amenity}
        </span>
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="flex items-start justify-between gap-3">
          <p className="text-[16px] font-bold leading-snug text-text-primary">{place.shortName}</p>
          <span className="shrink-0 rounded-full bg-verified-bright px-3 py-1.5 text-[10px] font-bold leading-tight text-verified-deep">
            {place.openLabel}
          </span>
        </div>

        <div className="flex gap-6 pt-2 text-[11px] text-text-secondary">
          <span className="flex items-start gap-1.5">
            <Phone size={13} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
            <span>
              <span className="block">{t("clinic.emergencyHotline")}</span>
              <span className="block font-bold text-text-primary">{place.phone}</span>
            </span>
          </span>
          <span className="flex items-start gap-1.5">
            <MapPin size={13} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
            <span className="font-semibold text-text-primary">
              {t("list.distanceFromYou", { km: place.distanceKm.toFixed(1) })}
            </span>
          </span>
        </div>

        <div className="grid grid-cols-3 gap-2 pt-3">
          <div className="rounded-xl bg-background-alt p-2.5">
            <p className="text-[10px] text-text-tertiary">{t("list.leadDoctorLabel")}</p>
            <p className="pt-0.5 text-[11px] font-bold leading-tight text-text-primary">{doctor.name}</p>
            <p className="pt-0.5 text-[10px] text-text-secondary">{doctor.tags[0]}</p>
          </div>
          <div className="rounded-xl bg-background-alt p-2.5">
            <p className="text-[10px] text-text-tertiary">{price.title}</p>
            <p className="pt-0.5 text-[12px] font-bold text-text-primary">{formatVnd(price.price)}</p>
            <p className="pt-0.5 text-[10px] text-success-text">{price.badge}</p>
          </div>
          <div className="rounded-xl bg-info/50 p-2.5">
            <p className="text-[10px] font-bold text-primary-dark">{t("booking.liveSync")}</p>
            <p className="pt-0.5 text-[10px] leading-tight text-text-secondary">{t("booking.subtitle")}</p>
          </div>
        </div>

        <p className="flex items-center gap-1.5 pt-3 text-[11px] font-semibold text-success-text">
          <BadgeCheck size={13} className="shrink-0" aria-hidden="true" />
          {t("booking.catSynced")}
        </p>

        <div className="flex gap-2 pt-3">
          <Link
            to={`/map/clinics/${place.id}`}
            className="flex items-center justify-center gap-1.5 rounded-xl bg-deco-backdrop px-4 py-3 text-[12px] font-bold text-primary-dark hover:bg-chip-bg"
          >
            <ShieldCheck size={14} aria-hidden="true" />
            {t("actions.detail")}
          </Link>
          <Link
            to={`/map/clinics/${place.id}`}
            className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-4 py-3 text-[12px] font-bold text-white hover:bg-primary"
          >
            <BadgeCheck size={14} aria-hidden="true" />
            {t("clinic.bookCta")}
          </Link>
        </div>
      </div>
    </div>
  );
}

/* -------------------------------------------------------------------- page */

export function CatCareMapPage() {
  const { t } = useTranslation("map");
  const [filter, setFilter] = useState<MockFilterChip["id"]>("all");
  const [activeId, setActiveId] = useState(DESIGN_MOCK_PLACES[0].id);
  const [query, setQuery] = useState(DESIGN_MOCK_AREA);

  const visible = DESIGN_MOCK_PLACES.filter((p) => matchesFilter(p, filter));
  const featured = DESIGN_MOCK_PLACES[0];
  const activePlace = DESIGN_MOCK_PLACES.find((p) => p.id === activeId) ?? featured;

  const mapBanner = (
    <div className="absolute left-3 top-3 flex items-center gap-2 rounded-full bg-surface/95 px-3 py-1.5 text-[11px] font-semibold text-text-primary shadow-xs">
      <span className="size-2 shrink-0 rounded-full bg-success" aria-hidden="true" />
      {t("map.nearbyBanner", { count: DESIGN_MOCK_NEARBY_COUNT })}
    </div>
  );

  return (
    <>
      {/* ------------------------------------------------------- mobile */}
      <div className="flex flex-col gap-4 px-4 py-4 lg:hidden">
        <div>
          <h1 className="text-h3 font-bold text-text-primary">{t("page.title")}</h1>
          <p className="pt-1 text-[12px] text-text-secondary">{t("page.subtitle")}</p>
        </div>

        <label className="flex items-center gap-2 rounded-xl bg-surface px-3 py-2.5 shadow-xs">
          <Search size={15} className="shrink-0 text-text-tertiary" aria-hidden="true" />
          <span className="sr-only">{t("search.label")}</span>
          <input
            value={query}
            onChange={(e) => { setQuery(e.target.value); }}
            placeholder={t("search.placeholder")}
            className="min-w-0 flex-1 bg-transparent text-[13px] text-text-primary outline-none placeholder:text-text-tertiary"
          />
        </label>

        <FilterChips
          filters={DESIGN_MOCK_FILTERS}
          active={filter}
          onChange={setFilter}
          className="-mx-4 overflow-x-auto px-4 pb-1"
        />

        <MapCanvas
          places={visible}
          activeId={activeId}
          onSelect={setActiveId}
          className="aspect-[358/320] w-full"
        >
          {mapBanner}
        </MapCanvas>

        <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-text-tertiary">
          <MapIcon size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t("preview.map")}
        </p>

        <MobileFeaturedCard place={activePlace} />

        <div className="flex items-start justify-between gap-3">
          <h2 className="text-[17px] font-bold text-text-primary">{t("list.nearbyTitle")}</h2>
          <button type="button" className="shrink-0 text-right text-[12px] font-semibold text-primary-dark hover:underline">
            {t("list.nearbySeeAll", { count: DESIGN_MOCK_NEARBY_COUNT })}
          </button>
        </div>

        <div className="flex flex-col gap-2.5">
          {visible
            .filter((p) => p.id !== activePlace.id)
            .map((p) => (
              <MobileNearbyRow key={p.id} place={p} />
            ))}
        </div>

        {visible.length === 0 ? (
          <p className="rounded-xl bg-surface p-4 text-center text-[12px] text-text-secondary shadow-xs">
            {t("list.empty")}
          </p>
        ) : null}

        <DisclaimerBanner variant="footerLine" />
      </div>

      {/* ------------------------------------------------------ desktop */}
      <div className="hidden flex-col gap-5 lg:flex">
        {/* Dải mạng lưới ISFM */}
        <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
          <div className="flex items-start gap-4">
            <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
              <MapIcon size={22} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <h1 className="text-[20px] font-bold text-primary-dark">{t("network.title")}</h1>
                <span className="flex items-center gap-1 rounded-full bg-success-bg px-2.5 py-1 text-[11px] font-bold text-success-text">
                  <BadgeCheck size={12} aria-hidden="true" />
                  {t("network.partners", { count: DESIGN_MOCK_PARTNER_COUNT })}
                </span>
              </div>
              <p className="pt-1.5 text-[13px] text-text-secondary">{t("network.body")}</p>
              <div className="flex flex-wrap gap-2 pt-3.5">
                <span className="flex items-center gap-1.5 rounded-xl bg-background-alt px-3 py-2 text-[12px] font-semibold text-text-secondary">
                  <Crosshair size={13} className="text-primary-dark" aria-hidden="true" />
                  {t("network.areaChip", { area: DESIGN_MOCK_AREA })}
                </span>
                <button
                  type="button"
                  className="flex items-center gap-1.5 rounded-xl bg-chip-bg px-3 py-2 text-[12px] font-semibold text-primary-dark hover:bg-info"
                >
                  <MapPin size={13} aria-hidden="true" />
                  {t("network.changeArea")}
                </button>
              </div>
              <p className="pt-2 text-[11px] text-text-tertiary">{t("preview.location")}</p>
            </div>
          </div>
        </section>

        {/* Split: danh sách 376px + bản đồ 540px = khung 944px của AppLayout */}
        <div className="flex items-start gap-7">
          <div className="flex w-[376px] shrink-0 flex-col gap-4">
            <div className="rounded-2xl bg-surface p-4 shadow-brand-md">
              <div className="flex items-center gap-2">
                <label className="flex min-w-0 flex-1 items-center gap-2 rounded-xl bg-background-alt px-3 py-2.5">
                  <Search size={15} className="shrink-0 text-text-tertiary" aria-hidden="true" />
                  <span className="sr-only">{t("search.label")}</span>
                  <input
                    value={query}
                    onChange={(e) => { setQuery(e.target.value); }}
                    placeholder={t("search.placeholder")}
                    className="min-w-0 flex-1 bg-transparent text-[13px] text-text-primary outline-none placeholder:text-text-tertiary"
                  />
                </label>
                <button
                  type="button"
                  aria-label={t("search.advanced")}
                  className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark"
                >
                  <SlidersHorizontal size={15} aria-hidden="true" />
                </button>
              </div>
              <FilterChips
                filters={DESIGN_MOCK_FILTERS}
                active={filter}
                onChange={setFilter}
                className="-mx-1 overflow-x-auto px-1 pt-3"
              />
            </div>

            <div className="flex max-h-[574px] flex-col gap-4 overflow-y-auto pr-1">
              {visible.map((p) => (
                <DesktopPlaceCard
                  key={p.id}
                  place={p}
                  active={p.id === activeId}
                  onSelect={() => { setActiveId(p.id); }}
                />
              ))}
              {visible.length === 0 ? (
                <p className="rounded-2xl bg-surface p-5 text-center text-[12px] text-text-secondary shadow-xs">
                  {t("list.empty")}
                </p>
              ) : null}
            </div>
          </div>

          <MapCanvas places={visible} activeId={activeId} onSelect={setActiveId} className="h-[700px] min-w-0 flex-1">
            <div className="absolute left-4 top-4 flex items-center gap-2 rounded-xl bg-surface/95 px-3.5 py-2 text-[12px] text-text-secondary shadow-xs">
              <span className="size-2.5 shrink-0 rounded-full bg-primary-dark" aria-hidden="true" />
              {t("map.scanning")}
              <span className="rounded-md bg-background-alt px-1.5 py-0.5 font-bold text-text-primary">
                {DESIGN_MOCK_AREA}
              </span>
              <span className="font-semibold">{t("map.scanResult", { count: DESIGN_MOCK_CERTIFIED_COUNT })}</span>
            </div>
            <DesktopMapPopup place={activePlace} />
          </MapCanvas>
        </div>

        <p className="flex items-center gap-1.5 text-[11px] text-text-tertiary">
          <MapIcon size={13} className="shrink-0" aria-hidden="true" />
          {t("preview.map")}
        </p>

        {/* Hàng hỗ trợ nhanh */}
        <section>
          <h2 className="sr-only">{t("quickServices.title")}</h2>
          <div className="grid grid-cols-4 gap-4">
            {DESIGN_MOCK_QUICK_SERVICES.map((s) => {
              const Icon = QUICK_SERVICE_ICON[s.icon];
              return (
                <article key={s.id} className="flex gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
                  <span
                    className={cn(
                      "flex size-10 shrink-0 items-center justify-center rounded-xl",
                      QUICK_SERVICE_TONE[s.tone],
                    )}
                  >
                    <Icon size={18} aria-hidden="true" />
                  </span>
                  <span className="min-w-0">
                    <span
                      className={cn(
                        "block text-[13px] font-bold leading-snug",
                        s.tone === "danger" ? "text-danger-text" : "text-primary-dark",
                      )}
                    >
                      {s.title}
                    </span>
                    <span className="block pt-1 text-[11px] leading-relaxed text-text-secondary">{s.body}</span>
                  </span>
                </article>
              );
            })}
          </div>
        </section>

        <DisclaimerBanner variant="footerLine" />
      </div>
    </>
  );
}
