import { useCallback, useEffect, useRef, useState } from "react";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Link, useSearchParams } from "react-router";
import {
  CalendarCheck,
  Crosshair,
  ExternalLink,
  Map as MapIcon,
  MapPin,
  Navigation,
  Phone,
  Search,
  X,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useBreakpoint } from "@/shared/lib/hooks/useBreakpoint";
import { useDebounce } from "@/shared/lib/hooks/useDebounce";
import { ErrorState, SkeletonLoader } from "@/shared/ui";
import { DisclaimerBanner } from "@/entities/disclaimer";
import { listNearbyPlaces, listPlaces } from "@/features/place";
import { acquireLocation, type UserLocation } from "./location";
import { PlaceMap } from "./PlaceMap";
import { DistanceLabel, KindChip, OsmSourceChip, PlaceBadges, RatingLine } from "./placeParts";
import {
  KIND_CHIP_CLASS,
  KIND_ICON,
  PLACE_KINDS,
  directionsUrl,
  distanceKm,
  distanceText,
  telHref,
  toViewPlace,
  type PlaceKind,
  type ViewPlace,
} from "./placeView";


type FilterId = "all" | PlaceKind;

type LocationStatus = "idle" | "requesting" | "ready" | "denied" | "unavailable" | "unsupported";

const NEARBY_RADIUS_KM = 50;

function googleMapsSearchUrl(location: UserLocation, query: string): string {
  const lat = location.latitude.toFixed(3);
  const lng = location.longitude.toFixed(3);
  return `https://www.google.com/maps/search/${encodeURIComponent(query)}/@${lat},${lng},14z`;
}

function MoreOnGoogleMaps({ location }: { location: UserLocation }) {
  const { t } = useTranslation("map");
  return (
    <a
      href={googleMapsSearchUrl(location, t("location.googleSearchQuery"))}
      target="_blank"
      rel="noreferrer"
      className="inline-flex items-center gap-1 pt-0.5 font-semibold text-primary-dark underline-offset-2 hover:underline"
    >
      {t("location.moreOnGoogle")}
      <ExternalLink size={11} aria-hidden="true" />
    </a>
  );
}
const DUPLICATE_RADIUS_KM = 0.12;

function matchesQuery(place: ViewPlace, query: string): boolean {
  if (!query) return true;
  const q = query.toLowerCase();
  return place.name.toLowerCase().includes(q) || place.address.toLowerCase().includes(q);
}

function PlaceName({ place, className }: { place: ViewPlace; className: string }) {
  if (place.source === "osm") return <span className={cn("block", className)}>{place.name}</span>;
  return (
    <Link to={`/map/clinics/${place.id}`} className={cn("block", className)}>
      {place.name}
    </Link>
  );
}

const FILTER_TONE: Record<FilterId, string> = {
  all: "bg-background-alt text-text-secondary hover:bg-chip-bg hover:text-primary-dark",
  clinic: "bg-chip-bg text-primary-dark hover:bg-info",
  emergency: "bg-danger-bg text-danger-text hover:bg-danger-bg/70",
  lab: "bg-info text-primary-dark hover:bg-chip-bg",
  store: "bg-secondary/25 text-secondary-text-on hover:bg-secondary/40",
};

function FilterChips({
  filters,
  active,
  onChange,
  className,
}: {
  filters: { id: FilterId; count: number }[];
  active: FilterId;
  onChange: (id: FilterId) => void;
  className?: string;
}) {
  const { t } = useTranslation("map");
  return (
    <div role="group" aria-label={t("filters.label")} className={cn("flex gap-2", className)}>
      {filters.map((f) => {
        const Icon = f.id === "all" ? null : KIND_ICON[f.id];
        const selected = active === f.id;
        return (
          <button
            key={f.id}
            type="button"
            onClick={() => {
              onChange(f.id);
            }}
            aria-pressed={selected}
            className={cn(
              "flex min-h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full px-3 text-[12px] font-semibold transition-colors",
              selected ? "bg-primary-dark text-white" : FILTER_TONE[f.id],
            )}
          >
            {Icon ? <Icon size={13} className="shrink-0" aria-hidden="true" /> : null}
            {f.id === "all" ? t("filters.all") : t(`kind.${f.id}`)}
            <span
              className={cn(
                "min-w-5 rounded-full px-1.5 text-center text-[10px] font-bold",
                selected ? "bg-white/20" : "bg-surface/80",
              )}
            >
              {f.count}
            </span>
          </button>
        );
      })}
    </div>
  );
}

function SearchField({
  value,
  onChange,
  className,
}: {
  value: string;
  onChange: (v: string) => void;
  className?: string;
}) {
  const { t } = useTranslation("map");
  return (
    <label
      className={cn(
        "flex min-h-11 min-w-0 items-center gap-2 rounded-xl px-3 focus-within:outline focus-within:outline-[var(--focus-ring-width)] focus-within:outline-[var(--focus-ring-color)]",
        className,
      )}
    >
      <Search size={15} className="shrink-0 text-text-tertiary" aria-hidden="true" />
      <span className="sr-only">{t("search.label")}</span>
      <input
        type="search"
        value={value}
        onChange={(event) => {
          onChange(event.target.value);
        }}
        placeholder={t("search.placeholder")}
        className="min-w-0 flex-1 bg-transparent text-[13px] text-text-primary placeholder:text-text-tertiary focus-visible:outline-none! [&::-webkit-search-cancel-button]:hidden"
      />
      {value ? (
        <button
          type="button"
          onClick={() => {
            onChange("");
          }}
          aria-label={t("search.clear")}
          className="flex size-7 shrink-0 items-center justify-center rounded-full text-text-tertiary hover:bg-chip-bg hover:text-primary-dark"
        >
          <X size={14} aria-hidden="true" />
        </button>
      ) : null}
    </label>
  );
}

function PlaceActions({ place, size = "md" }: { place: ViewPlace; size?: "sm" | "md" }) {
  const { t } = useTranslation("map");
  const directions = directionsUrl(place);
  const base = cn(
    "flex min-h-9 flex-1 items-center justify-center gap-1 whitespace-nowrap rounded-lg px-2 font-bold transition-colors",
    size === "sm" ? "text-[11px]" : "text-[13px]",
  );
  return (
    <div className="flex gap-1.5">
      {place.source === "catcheck" ? (
        <Link
          to={`/map/clinics/${place.id}#booking`}
          className={cn(base, "bg-primary-dark text-white hover:bg-primary")}
        >
          <CalendarCheck size={13} aria-hidden="true" />
          {t("actions.book")}
        </Link>
      ) : null}
      {place.phone ? (
        <a href={telHref(place.phone)} className={cn(base, "bg-danger-bg text-danger-text hover:bg-danger-bg/70")}>
          <Phone size={13} aria-hidden="true" />
          {t("actions.callShort")}
        </a>
      ) : null}
      {directions ? (
        <a
          href={directions}
          target="_blank"
          rel="noreferrer"
          className={cn(base, "bg-chip-bg text-primary-dark hover:bg-info")}
        >
          <Navigation size={13} aria-hidden="true" />
          {t("actions.directions")}
        </a>
      ) : null}
    </div>
  );
}

function SpecialtyChips({ specialties, className }: { specialties: string[]; className?: string }) {
  const { t } = useTranslation("map");
  if (specialties.length === 0) return null;
  return (
    <ul aria-label={t("list.specialties")} className={cn("flex flex-wrap gap-1.5", className)}>
      {specialties.map((s) => (
        <li
          key={s}
          className="whitespace-nowrap rounded-md bg-background-alt px-2 py-0.5 text-[11px] text-text-secondary"
        >
          {s}
        </li>
      ))}
    </ul>
  );
}

function ListSkeleton({ count = 3 }: { count?: number }) {
  return (
    <>
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="shrink-0">
          <SkeletonLoader shape="card" className="h-40 rounded-2xl bg-surface" />
        </li>
      ))}
    </>
  );
}


function MobileSelectedCard({ place, onClose }: { place: ViewPlace; onClose: () => void }) {
  const { t } = useTranslation("map");
  return (
    <article className="rounded-2xl bg-surface p-4 shadow-brand-md">
      <div className="flex items-start gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
          <span className="whitespace-nowrap rounded-full bg-chip-bg px-2.5 py-1 text-[10px] font-bold text-primary-dark">
            {t("list.selected")}
          </span>
          <KindChip kind={place.kind} />
          {place.source === "osm" ? <OsmSourceChip /> : null}
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={t("actions.closeCard")}
          className="flex size-8 shrink-0 items-center justify-center rounded-full bg-background-alt text-text-secondary hover:bg-chip-bg"
        >
          <X size={15} aria-hidden="true" />
        </button>
      </div>

      <PlaceName
        place={place}
        className="pt-2 text-[20px] font-bold leading-tight text-text-primary hover:text-primary-dark"
      />

      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-2">
        {place.source === "catcheck" ? <RatingLine rating={place.rating} reviewCount={place.reviewCount} /> : null}
        <DistanceLabel km={place.distanceKm} className="text-primary-dark" />
      </div>
      {place.address ? (
        <p className="flex items-start gap-1.5 pt-1.5 text-[12px] text-text-secondary">
          <MapPin size={13} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
          {place.address}
        </p>
      ) : null}
      {place.source === "osm" ? (
        <p className="pt-1.5 text-[11px] leading-relaxed text-text-tertiary">{t("source.osmNote")}</p>
      ) : null}

      {place.badges.length > 0 ? (
        <div className="flex flex-wrap gap-1.5 pt-2.5">
          <PlaceBadges badges={place.badges} />
        </div>
      ) : null}

      {place.specialties.length > 0 ? (
        <>
          <p className="pt-3 text-[11px] font-semibold text-text-tertiary">{t("list.specialties")}</p>
          <SpecialtyChips specialties={place.specialties} className="pt-1.5" />
        </>
      ) : null}

      <div className="pt-4">
        <PlaceActions place={place} size="sm" />
      </div>
    </article>
  );
}

function MobilePlaceRow({
  place,
  onSelect,
  isRecommended = false,
}: {
  place: ViewPlace;
  onSelect: () => void;
  isRecommended?: boolean;
}) {
  const { t } = useTranslation("map");
  const Icon = KIND_ICON[place.kind];
  const directions = directionsUrl(place);
  return (
    <li
      className={cn(
        "flex items-center gap-2.5 rounded-xl bg-surface p-3 shadow-xs transition-all",
        isRecommended && "border border-primary-dark/20 bg-primary-dark/5",
      )}
    >
      <button
        type="button"
        onClick={onSelect}
        aria-label={t("actions.showOnMapNamed", { name: place.name })}
        className={cn("flex size-10 shrink-0 items-center justify-center rounded-lg", KIND_CHIP_CLASS[place.kind])}
      >
        <Icon size={16} aria-hidden="true" />
      </button>
      {place.source === "osm" ? (
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            {isRecommended ? (
              <span className="inline-flex items-center gap-0.5 whitespace-nowrap rounded-sm bg-primary-dark px-1.5 py-0.5 text-[10px] font-bold text-white">
                ⭐ {t("list.recommendedBadge")}
              </span>
            ) : null}
            <span className="block truncate text-[13px] font-bold text-text-primary">{place.name}</span>
          </div>
          <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5 pt-1 text-[11px] text-text-secondary">
            <OsmSourceChip />
            <span className="truncate">{place.area || place.address}</span>
            <DistanceLabel km={place.distanceKm} className="text-primary-dark" />
          </span>
        </div>
      ) : (
        <Link to={`/map/clinics/${place.id}`} className="min-w-0 flex-1">
          <div className="flex items-center gap-1.5">
            {isRecommended ? (
              <span className="inline-flex items-center gap-0.5 whitespace-nowrap rounded-sm bg-primary-dark px-1.5 py-0.5 text-[10px] font-bold text-white">
                ⭐ {t("list.recommendedBadge")}
              </span>
            ) : null}
            <span className="block truncate text-[13px] font-bold text-text-primary">{place.name}</span>
          </div>
          <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5 pt-1 text-[11px] text-text-secondary">
            <RatingLine rating={place.rating} reviewCount={place.reviewCount} hideEmpty />
            <span className="truncate">{place.area}</span>
            <DistanceLabel km={place.distanceKm} className="text-primary-dark" />
          </span>
        </Link>
      )}
      {directions ? (
        <a
          href={directions}
          target="_blank"
          rel="noreferrer"
          aria-label={t("actions.directionsNamed", { name: place.name })}
          className="flex size-9 shrink-0 items-center justify-center rounded-md bg-background-alt text-primary-dark hover:bg-chip-bg"
        >
          <Navigation size={15} aria-hidden="true" />
        </a>
      ) : null}
    </li>
  );
}


function DesktopPlaceCard({
  place,
  active,
  onSelect,
  isRecommended = false,
}: {
  place: ViewPlace;
  active: boolean;
  onSelect: () => void;
  isRecommended?: boolean;
}) {
  const { t } = useTranslation("map");
  const distance = place.distanceKm === null ? null : distanceText(place.distanceKm);
  const ref = useRef<HTMLLIElement>(null);
  useEffect(() => {
    if (active) ref.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [active]);
  return (
    <li
      ref={ref}
      className={cn(
        "relative shrink-0 overflow-hidden rounded-xl bg-surface p-3 shadow-xs transition-all",
        active ? "ring-2 ring-primary-dark" : "hover:shadow-xs",
        isRecommended && "border border-primary-dark/30 bg-gradient-to-r from-primary-dark/5 to-transparent",
      )}
    >
      {active ? <span aria-hidden="true" className="absolute inset-y-0 left-0 w-1 bg-primary-dark" /> : null}
      <div className="flex items-start gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1">
          {isRecommended ? (
            <span className="inline-flex items-center gap-0.5 whitespace-nowrap rounded-sm bg-primary-dark px-1.5 py-0.5 text-[10px] font-bold text-white">
              ⭐ {t("list.recommendedBadge")}
            </span>
          ) : null}
          <KindChip kind={place.kind} />
          {place.source === "osm" ? <OsmSourceChip /> : null}
          <PlaceBadges badges={place.badges} />
        </div>
        <button
          type="button"
          onClick={onSelect}
          aria-pressed={active}
          aria-label={t("actions.showOnMapNamed", { name: place.name })}
          title={t("actions.showOnMap")}
          className={cn(
            "flex h-8 shrink-0 items-center gap-1 rounded-lg px-2 text-[11px] font-bold transition-colors",
            active ? "bg-primary-dark text-white" : "bg-background-alt text-primary-dark hover:bg-chip-bg",
          )}
        >
          <MapPin size={13} aria-hidden="true" />
          {distance ? t(distance.key, { value: distance.value }) : t("actions.mapShort")}
        </button>
      </div>

      <PlaceName place={place} className="pt-1.5 text-[16px] font-bold leading-snug text-primary-dark hover:underline" />
      {place.address ? <p className="pt-0.5 text-[11px] text-text-tertiary">{place.address}</p> : null}

      {place.source === "osm" ? (
        <p className="pt-1.5 text-[10px] leading-relaxed text-text-tertiary">{t("source.osmNote")}</p>
      ) : (
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1 pt-2">
          <RatingLine rating={place.rating} reviewCount={place.reviewCount} />
          <SpecialtyChips specialties={place.specialties} className="contents" />
        </div>
      )}

      <div className="pt-2">
        <PlaceActions place={place} size="sm" />
      </div>
    </li>
  );
}

function DesktopMapPopup({ place, onClose }: { place: ViewPlace; onClose: () => void }) {
  const { t } = useTranslation("map");
  return (
    <div className="absolute bottom-10 left-4 z-10 w-[min(380px,calc(100%-2rem))] rounded-2xl bg-surface/95 p-4 shadow-brand-lg backdrop-blur-sm">
      <div className="flex items-start gap-2">
        <div className="flex min-w-0 flex-1 flex-wrap items-center gap-1.5">
          <KindChip kind={place.kind} />
          {place.source === "osm" ? <OsmSourceChip /> : null}
          <PlaceBadges badges={place.badges} />
        </div>
        <button
          type="button"
          onClick={onClose}
          aria-label={t("actions.closeCard")}
          className="flex size-7 shrink-0 items-center justify-center rounded-full bg-background-alt text-text-secondary hover:bg-chip-bg"
        >
          <X size={14} aria-hidden="true" />
        </button>
      </div>
      <PlaceName place={place} className="pt-2 text-[16px] font-bold leading-snug text-primary-dark hover:underline" />
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-1.5">
        {place.source === "catcheck" ? <RatingLine rating={place.rating} reviewCount={place.reviewCount} /> : null}
        <DistanceLabel km={place.distanceKm} className="text-primary-dark" />
      </div>
      {place.address ? (
        <p className="flex items-start gap-1.5 pt-1.5 text-[12px] text-text-secondary">
          <MapPin size={12} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
          {place.address}
        </p>
      ) : null}
      {place.phone ? (
        <p className="flex items-center gap-1.5 pt-1 text-[12px] text-text-secondary">
          <Phone size={12} className="shrink-0 text-primary-dark" aria-hidden="true" />
          <span className="font-bold text-text-primary">{place.phone}</span>
        </p>
      ) : null}
      {place.source === "osm" ? (
        <div className="pt-3">
          <PlaceActions place={place} size="sm" />
        </div>
      ) : (
        <div className="flex gap-2 pt-3">
          <Link
            to={`/map/clinics/${place.id}`}
            className="flex min-h-10 flex-1 items-center justify-center rounded-xl bg-secondary px-3 text-[12px] font-bold text-secondary-text-on hover:bg-secondary-light"
          >
            {t("actions.detail")}
          </Link>
          <Link
            to={`/map/clinics/${place.id}#booking`}
            className="flex min-h-10 flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-3 text-[12px] font-bold text-white hover:bg-primary"
          >
            <CalendarCheck size={14} aria-hidden="true" />
            {t("actions.book")}
          </Link>
        </div>
      )}
    </div>
  );
}


export function CatCareMapPage() {
  const { t } = useTranslation("map");
  const isDesktop = useBreakpoint("lg");
  const [searchParams] = useSearchParams();
  const [filter, setFilter] = useState<FilterId>("all");
  const [highRatingOnly, setHighRatingOnly] = useState(false);
  const [areaFilter, setAreaFilter] = useState<"ALL" | "DUY_XUYEN" | "DA_NANG">("ALL");
  const [activeId, setActiveId] = useState(() => searchParams.get("place") ?? "");
  const [query, setQuery] = useState("");
  const debouncedQuery = useDebounce(query.trim(), 300);
  const [userLocation, setUserLocation] = useState<UserLocation | null>(null);
  const [locationStatus, setLocationStatus] = useState<LocationStatus>("idle");
  const cancelLocationRequest = useRef<(() => void) | null>(null);

  const stopLocationRequest = useCallback(() => {
    cancelLocationRequest.current?.();
    cancelLocationRequest.current = null;
  }, []);

  const requestUserLocation = useCallback(() => {
    const geolocation = "geolocation" in navigator ? navigator.geolocation : undefined;
    if (!geolocation) {
      setLocationStatus("unsupported");
      return;
    }
    stopLocationRequest();
    setLocationStatus("requesting");
    cancelLocationRequest.current = acquireLocation(
      geolocation,
      (location) => {
        setUserLocation(location);
        setLocationStatus("ready");
      },
      setLocationStatus,
    );
  }, [stopLocationRequest]);

  const locateFromButton = useCallback(() => {
    setActiveId("");
    requestUserLocation();
  }, [requestUserLocation]);

  useEffect(() => {
    requestUserLocation();
    return stopLocationRequest;
  }, [requestUserLocation, stopLocationRequest]);

  const placesQuery = useQuery({
    queryKey: ["place", "list", debouncedQuery, userLocation?.latitude, userLocation?.longitude],
    queryFn: ({ signal }) =>
      listPlaces(
        {
          query: debouncedQuery || undefined,
          latitude: userLocation?.latitude,
          longitude: userLocation?.longitude,
        },
        signal,
      ),
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });

  const nearbyLat = userLocation ? Math.round(userLocation.latitude * 100) / 100 : null;
  const nearbyLng = userLocation ? Math.round(userLocation.longitude * 100) / 100 : null;
  const nearbyQuery = useQuery({
    queryKey: ["place", "nearby", nearbyLat, nearbyLng, NEARBY_RADIUS_KM],
    queryFn: ({ signal }) =>
      listNearbyPlaces({ latitude: nearbyLat ?? 0, longitude: nearbyLng ?? 0, radiusKm: NEARBY_RADIUS_KM }, signal),
    enabled: nearbyLat !== null && nearbyLng !== null,
    staleTime: 60 * 60_000,
    retry: 1,
  });

  const catcheckPlaces = (placesQuery.data ?? []).map((place) => toViewPlace(place, userLocation));
  const osmAll = userLocation
    ? (nearbyQuery.data ?? [])
        .map((place) => toViewPlace(place, userLocation))
        .filter((p) => !catcheckPlaces.some((c) => distanceKm(c, p) < DUPLICATE_RADIUS_KM))
    : [];
  const places = [...catcheckPlaces, ...osmAll.filter((p) => matchesQuery(p, debouncedQuery))];
  if (userLocation) places.sort((a, b) => (a.distanceKm ?? Infinity) - (b.distanceKm ?? Infinity));
  const nearbyMessage = !userLocation
    ? null
    : nearbyQuery.isPending
      ? t("location.nearbyLoading")
      : nearbyQuery.isError
        ? t("location.nearbyError")
        : osmAll.length > 0
          ? t("location.nearbyFound", { count: osmAll.length })
          : t("location.nearbyNone", { radius: NEARBY_RADIUS_KM });

  const visible = places.filter((p) => {
    if (filter !== "all" && p.kind !== filter) return false;
    if (highRatingOnly && (p.rating < 4.5 || p.reviewCount < 1)) return false;
    if (areaFilter === "DUY_XUYEN") {
      const text = `${p.area} ${p.address} ${p.name}`.toLowerCase();
      return (
        text.includes("duy xuyên") ||
        text.includes("nam phước") ||
        text.includes("hội an") ||
        text.includes("điện bàn") ||
        text.includes("quảng nam")
      );
    }
    if (areaFilter === "DA_NANG") {
      const text = `${p.area} ${p.address} ${p.name}`.toLowerCase();
      return (
        text.includes("đà nẵng") ||
        text.includes("hải châu") ||
        text.includes("sơn trà") ||
        text.includes("thanh khê") ||
        text.includes("ngũ hành sơn") ||
        text.includes("cẩm lệ") ||
        text.includes("liên chiểu")
      );
    }
    return true;
  });

  // Smart recommendation: Select clinics with rating >= 4.5 and reviewCount >= 5
  // If user location is known, pick closest among top-rated. Otherwise, highest reviews.
  const recommendedPlace = (() => {
    if (visible.length === 0) return null;
    const topRated = visible.filter((p) => p.rating >= 4.5 && p.reviewCount >= 5);
    if (topRated.length > 0) {
      if (userLocation) {
        return topRated.slice().sort((a, b) => (a.distanceKm ?? Infinity) - (b.distanceKm ?? Infinity))[0];
      }
      return topRated.slice().sort((a, b) => b.reviewCount - a.reviewCount)[0];
    }
    const withRating = visible.filter((p) => p.rating >= 4.5);
    if (withRating.length > 0) return withRating[0];
    return visible[0];
  })();

  const activePlace = visible.find((p) => p.id === activeId);
  const selectedId = activePlace?.id ?? "";

  const counts = Object.fromEntries(
    PLACE_KINDS.map((kind) => [kind, places.filter((p) => p.kind === kind).length]),
  ) as Record<PlaceKind, number>;
  const filters: { id: FilterId; count: number }[] = [
    { id: "all", count: places.length },
    ...PLACE_KINDS.filter((kind) => counts[kind] > 0 || kind === filter).map((kind) => ({
      id: kind,
      count: counts[kind],
    })),
  ];

  const accuracy = Math.max(1, Math.round(userLocation?.accuracy ?? 0));
  const approximate = (userLocation?.accuracy ?? 0) > 100;
  const locationMessage =
    locationStatus === "requesting"
      ? t("location.requesting")
      : locationStatus === "ready"
        ? t(approximate ? "location.approximate" : "location.ready", { accuracy })
        : locationStatus === "denied"
          ? t("location.denied")
          : locationStatus === "unsupported" || locationStatus === "unavailable"
            ? t("location.unavailable")
            : t("location.manual");
  const locateLabel = locationStatus === "ready" ? t("location.refresh") : t("location.use");

  const resetFilters = () => {
    setFilter("all");
    setQuery("");
    setHighRatingOnly(false);
    setAreaFilter("ALL");
  };

  const isFiltered = filter !== "all" || query.trim() !== "" || highRatingOnly || areaFilter !== "ALL";
  const showSkeleton = placesQuery.isPending;
  const showError = placesQuery.isError && !placesQuery.data;
  const showEmpty = !showSkeleton && !showError && visible.length === 0;

  const statusBlock = showError ? (
    <ErrorState
      title={t("list.loadError")}
      onRetry={() => {
        void placesQuery.refetch();
      }}
      className="rounded-2xl bg-surface py-8 shadow-xs"
    />
  ) : showEmpty ? (
    <div className="flex flex-col items-center gap-3 rounded-2xl bg-surface px-5 py-8 text-center shadow-xs">
      <MapPin size={22} className="text-text-tertiary" aria-hidden="true" />
      <p className="text-[13px] text-text-secondary">
        {query.trim() ? t("list.emptyQuery", { query: query.trim() }) : t("list.empty")}
      </p>
      {isFiltered ? (
        <button
          type="button"
          onClick={resetFilters}
          className="min-h-10 rounded-xl bg-chip-bg px-4 text-[12px] font-bold text-primary-dark hover:bg-info"
        >
          {t("list.reset")}
        </button>
      ) : null}
    </div>
  ) : null;

  const mapBanner = (
    <div className="pointer-events-none absolute left-3 top-3 z-10 flex max-w-[calc(100%-5.5rem)] items-center gap-2 rounded-full bg-surface/95 px-3 py-1.5 text-[11px] font-semibold text-text-primary shadow-xs">
      <span className="size-2 shrink-0 rounded-full bg-success" aria-hidden="true" />
      <span className="truncate">
        {userLocation ? `${t("map.nearYou")} · ` : ""}
        {t("map.onMap", { count: visible.length })}
      </span>
    </div>
  );

  if (!isDesktop) {
    const nearestPlace = recommendedPlace || (visible.length > 0 ? visible[0] : null);
    const others = visible.filter((p) => p.id !== selectedId && p.id !== nearestPlace?.id);
    return (
      <div className="flex flex-col gap-3 px-4 py-4">
        <div>
          <h1 className="text-h3 font-bold text-text-primary">{t("page.title")}</h1>
          <p className="pt-1 text-[12px] leading-relaxed text-text-secondary">{t("page.subtitle")}</p>
        </div>

        <SearchField value={query} onChange={setQuery} className="bg-surface shadow-xs" />

        <div className="relative -mx-4">
          <FilterChips
            filters={filters}
            active={filter}
            onChange={setFilter}
            className="overflow-x-auto px-4 pb-1 [scrollbar-width:none]"
          />
        </div>

        {/* Quick Area & Rating Filter Pills */}
        <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
          <button
            type="button"
            onClick={() => {
              setHighRatingOnly((prev) => !prev);
            }}
            aria-pressed={highRatingOnly}
            className={cn(
              "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
              highRatingOnly
                ? "bg-amber-500 text-white shadow-xs"
                : "border border-border/70 bg-surface text-text-secondary hover:border-amber-400 hover:text-text-primary",
            )}
          >
            {t("filters.topRated", { count: places.filter((p) => p.rating >= 4.5).length })}
          </button>
          <button
            type="button"
            onClick={() => {
              setAreaFilter((prev) => (prev === "DUY_XUYEN" ? "ALL" : "DUY_XUYEN"));
            }}
            aria-pressed={areaFilter === "DUY_XUYEN"}
            className={cn(
              "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
              areaFilter === "DUY_XUYEN"
                ? "bg-primary-dark text-white shadow-xs"
                : "border border-border/70 bg-surface text-text-secondary hover:border-primary/50 hover:text-primary-dark",
            )}
          >
            <MapPin size={11} aria-hidden="true" />
            {t("filters.duyXuyen")}
          </button>
          <button
            type="button"
            onClick={() => {
              setAreaFilter((prev) => (prev === "DA_NANG" ? "ALL" : "DA_NANG"));
            }}
            aria-pressed={areaFilter === "DA_NANG"}
            className={cn(
              "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
              areaFilter === "DA_NANG"
                ? "bg-primary-dark text-white shadow-xs"
                : "border border-border/70 bg-surface text-text-secondary hover:border-primary/50 hover:text-primary-dark",
            )}
          >
            <MapPin size={11} aria-hidden="true" />
            {t("filters.daNang")}
          </button>
        </div>

        <PlaceMap
          places={visible}
          activeId={selectedId}
          userLocation={userLocation}
          isLocating={locationStatus === "requesting"}
          onLocate={locateFromButton}
          onSelect={setActiveId}
          labels="active"
          fitPadding={{ top: 64, bottom: 40, left: 40, right: 56 }}
          className="aspect-[358/340] w-full shadow-brand-md rounded-lg"
        >
          {mapBanner}
        </PlaceMap>

        <div className="flex items-start gap-3 rounded-lg bg-surface p-3 shadow-xs">
          <Crosshair size={15} className="mt-0.5 shrink-0 text-primary-dark" aria-hidden="true" />
          <p className="min-w-0 flex-1 text-[11px] leading-relaxed text-text-secondary" aria-live="polite">
            {locationMessage}
            {nearbyMessage ? <span className="block pt-0.5">{nearbyMessage}</span> : null}
            {userLocation && places.length === 0 ? <MoreOnGoogleMaps location={userLocation} /> : null}
          </p>
          <button
            type="button"
            onClick={locateFromButton}
            disabled={locationStatus === "requesting"}
            className="min-h-9 shrink-0 whitespace-nowrap rounded-lg bg-chip-bg px-3 text-[12px] font-bold text-primary-dark hover:bg-info disabled:cursor-wait disabled:opacity-60"
          >
            {locateLabel}
          </button>
        </div>

        {activePlace ? (
          <MobileSelectedCard
            place={activePlace}
            onClose={() => {
              setActiveId("");
            }}
          />
        ) : null}

        {nearestPlace && nearestPlace.id !== selectedId ? (
          <>
            <h2 className="text-[14px] font-bold text-text-primary">{t("list.recommendedTitle")}</h2>
            <ul className="flex flex-col gap-2">
              <MobilePlaceRow
                key={nearestPlace.id}
                place={nearestPlace}
                isRecommended={true}
                onSelect={() => {
                  setActiveId(nearestPlace.id);
                }}
              />
            </ul>
          </>
        ) : null}

        {others.length > 0 || showSkeleton ? (
          <h2 className="text-[14px] font-bold text-text-primary pt-1">
            {nearestPlace && nearestPlace.id !== selectedId ? t("list.otherTitle") : activePlace ? t("list.otherTitle") : t("list.title")}
          </h2>
        ) : null}

        <ul className="flex flex-col gap-2">
          {showSkeleton ? <ListSkeleton count={2} /> : null}
          {others.map((p) => (
            <MobilePlaceRow
              key={p.id}
              place={p}
              onSelect={() => {
                setActiveId(p.id);
              }}
            />
          ))}
        </ul>

        {statusBlock}

        <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-text-tertiary">
          <MapIcon size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
          {t("list.dataNote")}
        </p>

        <DisclaimerBanner variant="footerLine" />
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-5">
      <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
        <div className="flex items-start gap-4">
          <span className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
            <MapIcon size={22} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <h1 className="text-[20px] font-bold text-primary-dark">{t("page.title")}</h1>
              {placesQuery.data ? (
                <span className="rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-bold text-primary-dark">
                  {t("page.placeCount", { count: places.length })}
                </span>
              ) : null}
            </div>
            <p className="pt-1.5 text-[13px] leading-relaxed text-text-secondary">{t("page.body")}</p>
            <div className="flex flex-wrap items-center gap-x-3 gap-y-2 pt-3.5">
              <button
                type="button"
                onClick={locateFromButton}
                disabled={locationStatus === "requesting"}
                className="flex min-h-9 items-center gap-1.5 rounded-xl bg-chip-bg px-3 text-[12px] font-semibold text-primary-dark hover:bg-info disabled:cursor-wait disabled:opacity-70"
              >
                <Crosshair size={13} aria-hidden="true" />
                {locateLabel}
              </button>
              <p className="min-w-0 flex-1 text-[11px] text-text-tertiary" aria-live="polite">
                {locationMessage}
                {nearbyMessage ? <span className="block pt-0.5">{nearbyMessage}</span> : null}
                {userLocation && places.length === 0 ? <MoreOnGoogleMaps location={userLocation} /> : null}
              </p>
            </div>
          </div>
        </div>
      </section>

      <div className="flex max-h-[860px] min-h-[600px] items-stretch gap-6">
        <div className="flex w-[360px] shrink-0 flex-col gap-3">
          <div className="shrink-0 rounded-xl bg-surface p-3 shadow-xs">
            <SearchField value={query} onChange={setQuery} className="bg-background-alt" />
            <FilterChips
              filters={filters}
              active={filter}
              onChange={setFilter}
              className="-mx-3 overflow-x-auto px-3 pt-2.5 [scrollbar-width:thin]"
            />
            {/* Quick Area & Rating Filter Pills */}
            <div className="flex flex-wrap items-center gap-1.5 pt-2.5">
              <button
                type="button"
                onClick={() => {
                  setHighRatingOnly((prev) => !prev);
                }}
                aria-pressed={highRatingOnly}
                className={cn(
                  "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
                  highRatingOnly
                    ? "bg-amber-500 text-white shadow-xs"
                    : "border border-border/70 bg-surface text-text-secondary hover:border-amber-400 hover:text-text-primary",
                )}
              >
                {t("filters.topRated", { count: places.filter((p) => p.rating >= 4.5).length })}
              </button>
              <button
                type="button"
                onClick={() => {
                  setAreaFilter((prev) => (prev === "DUY_XUYEN" ? "ALL" : "DUY_XUYEN"));
                }}
                aria-pressed={areaFilter === "DUY_XUYEN"}
                className={cn(
                  "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
                  areaFilter === "DUY_XUYEN"
                    ? "bg-primary-dark text-white shadow-xs"
                    : "border border-border/70 bg-surface text-text-secondary hover:border-primary/50 hover:text-primary-dark",
                )}
              >
                <MapPin size={11} aria-hidden="true" />
                {t("filters.duyXuyen")}
              </button>
              <button
                type="button"
                onClick={() => {
                  setAreaFilter((prev) => (prev === "DA_NANG" ? "ALL" : "DA_NANG"));
                }}
                aria-pressed={areaFilter === "DA_NANG"}
                className={cn(
                  "flex items-center gap-1 rounded-full px-2.5 py-1 text-[11px] font-bold transition-all active:scale-95",
                  areaFilter === "DA_NANG"
                    ? "bg-primary-dark text-white shadow-xs"
                    : "border border-border/70 bg-surface text-text-secondary hover:border-primary/50 hover:text-primary-dark",
                )}
              >
                <MapPin size={11} aria-hidden="true" />
                {t("filters.daNang")}
              </button>
            </div>
          </div>

          <ul className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto overscroll-contain rounded-xl bg-background-alt p-2.5">
            {showSkeleton ? <ListSkeleton /> : null}
            {recommendedPlace && (
              <li className="relative">
                <div className="mb-2 text-[12px] font-bold text-text-secondary px-1">{t("list.recommendedTitle")}</div>
                <DesktopPlaceCard
                  place={recommendedPlace}
                  active={recommendedPlace.id === selectedId}
                  isRecommended={true}
                  onSelect={() => {
                    setActiveId(recommendedPlace.id === selectedId ? "" : recommendedPlace.id);
                  }}
                />
              </li>
            )}
            {visible.filter((p) => p.id !== recommendedPlace?.id).length > 0 && (
              <li className="relative pt-1">
                <div className="mb-2 text-[12px] font-bold text-text-secondary px-1">{t("list.otherTitle")}</div>
              </li>
            )}
            {visible.filter((p) => p.id !== recommendedPlace?.id).map((p) => (
              <DesktopPlaceCard
                key={p.id}
                place={p}
                active={p.id === selectedId}
                onSelect={() => {
                  setActiveId(p.id === selectedId ? "" : p.id);
                }}
              />
            ))}
            {statusBlock ? <li className="shrink-0">{statusBlock}</li> : null}
          </ul>
        </div>

        <PlaceMap
          places={visible}
          activeId={selectedId}
          userLocation={userLocation}
          isLocating={locationStatus === "requesting"}
          onLocate={locateFromButton}
          onSelect={setActiveId}
          labels="all"
          className="min-w-0 flex-1 shadow-brand-md rounded-lg"
        >
          {mapBanner}
          {activePlace ? (
            <DesktopMapPopup
              place={activePlace}
              onClose={() => {
                setActiveId("");
              }}
            />
          ) : null}
        </PlaceMap>
      </div>

      <p className="flex items-start gap-1.5 text-[11px] leading-relaxed text-text-tertiary">
        <MapIcon size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
        {t("map.attribution")} {t("list.dataNote")}
      </p>

      <DisclaimerBanner variant="footerLine" />
    </div>
  );
}
