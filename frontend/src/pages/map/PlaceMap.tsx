import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import Map, { Marker, NavigationControl, ScaleControl, type MapRef } from "@vis.gl/react-maplibre";
import * as maplibregl from "maplibre-gl";
import type { PaddingOptions, StyleSpecification } from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
import { useTranslation } from "react-i18next";
import { Crosshair } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { mapTilesUrl } from "@/shared/config/env";
import { validCoordinates, type UserLocation } from "./location";
import { KIND_ICON, KIND_PIN_CLASS, type ViewPlace } from "./placeView";

/**
 * Bản đồ MapLibre dùng chung cho `/map` và mini-map ở chi tiết cơ sở.
 *
 * Tile: OSM raster mặc định, thay provider qua `VITE_MAP_TILES_URL`. Ghim lấy đúng
 * `latitude/longitude` của `GET /places`; cơ sở thiếu toạ độ hợp lệ thì không vẽ ghim.
 * Khung nhìn tự `fitBounds` theo các ghim đang hiển thị (+ vị trí người dùng nếu đã cấp quyền),
 * nên ghim không bị dồn cục ở zoom thành phố.
 */

const DEFAULT_MAP_TILE_TEMPLATES = ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"];
const mapTileTemplates = mapTilesUrl ? [`${mapTilesUrl}/{z}/{x}/{y}.png`] : DEFAULT_MAP_TILE_TEMPLATES;

const MAP_STYLE: StyleSpecification = {
  version: 8,
  sources: {
    mapTiles: {
      type: "raster" as const,
      tiles: mapTileTemplates,
      tileSize: 256,
      maxzoom: 19,
      attribution: mapTilesUrl ? undefined : "© OpenStreetMap contributors",
    },
  },
  layers: [{ id: "mapTiles", type: "raster" as const, source: "mapTiles" }],
};

const MAPLIBRE_WORKER_URL = "/maplibre/maplibre-gl-worker.mjs";

/** Trung tâm mặc định khi chưa có ghim nào (đang tải / danh sách rỗng): TP. Thủ Đức. */
const FALLBACK_CENTER = { latitude: 10.804, longitude: 106.735 };
const PLACE_ZOOM = 15;

type Point = [number, number];

interface PlaceMapProps {
  places: ViewPlace[];
  activeId?: string;
  userLocation?: UserLocation | null;
  onSelect?: (id: string) => void;
  /** Có thì vẽ nút "về vị trí hiện tại" trên bản đồ. */
  onLocate?: () => void;
  isLocating?: boolean;
  /** `active`: chỉ ghim đang chọn mới có nhãn tên (màn hẹp, tránh nhãn đè nhau). */
  labels?: "all" | "active";
  interactive?: boolean;
  fitPadding?: PaddingOptions;
  className?: string;
  children?: ReactNode;
}

const DEFAULT_PADDING: PaddingOptions = { top: 72, bottom: 48, left: 56, right: 64 };

export function PlaceMap({
  places,
  activeId = "",
  userLocation = null,
  onSelect,
  onLocate,
  isLocating = false,
  labels = "all",
  interactive = true,
  fitPadding = DEFAULT_PADDING,
  className,
  children,
}: PlaceMapProps) {
  const { t } = useTranslation("map");
  const mapRef = useRef<MapRef>(null);
  const [ready, setReady] = useState(false);

  const plotted = places.filter((place) => validCoordinates(place.latitude, place.longitude));
  const points: Point[] = plotted.map((place) => [place.longitude, place.latitude]);
  if (userLocation) points.push([userLocation.longitude, userLocation.latitude]);
  // Khoá dạng chuỗi: chỉ fit lại khi TẬP ghim đổi (lọc, tìm kiếm, có vị trí), không phải mỗi lần render.
  const boundsKey = JSON.stringify(points);
  const paddingKey = JSON.stringify(fitPadding);

  const fitToPoints = useCallback(
    (animate: boolean) => {
      const map = mapRef.current;
      const target = JSON.parse(boundsKey) as Point[];
      const padding = JSON.parse(paddingKey) as PaddingOptions;
      if (!map || target.length === 0) return false;
      if (target.length === 1) {
        map.easeTo({ center: target[0], zoom: PLACE_ZOOM, duration: animate ? 600 : 0 });
        return true;
      }
      const lngs = target.map((p) => p[0]);
      const lats = target.map((p) => p[1]);
      map.fitBounds(
        [
          [Math.min(...lngs), Math.min(...lats)],
          [Math.max(...lngs), Math.max(...lats)],
        ],
        { padding, maxZoom: PLACE_ZOOM, duration: animate ? 600 : 0 },
      );
      return true;
    },
    [boundsKey, paddingKey],
  );

  const firstFit = useRef(true);
  useEffect(() => {
    if (!ready) return;
    if (fitToPoints(!firstFit.current)) firstFit.current = false;
  }, [ready, fitToPoints]);

  const active = plotted.find((place) => place.id === activeId);
  const activeLatitude = active?.latitude;
  const activeLongitude = active?.longitude;
  useEffect(() => {
    if (!ready || activeLatitude === undefined || activeLongitude === undefined) return;
    const map = mapRef.current;
    if (!map) return;
    map.flyTo({ center: [activeLongitude, activeLatitude], zoom: Math.max(map.getZoom(), PLACE_ZOOM), duration: 700 });
  }, [ready, activeLatitude, activeLongitude]);

  const onlyPlace = plotted.length === 1 ? plotted[0] : undefined;
  const initialViewState = onlyPlace
    ? { latitude: onlyPlace.latitude, longitude: onlyPlace.longitude, zoom: PLACE_ZOOM }
    : { ...FALLBACK_CENTER, zoom: 12 };

  return (
    <div className={cn("relative isolate overflow-hidden rounded-2xl bg-background-alt", className)}>
      <Map
        ref={mapRef}
        mapLib={maplibregl}
        workerUrl={MAPLIBRE_WORKER_URL}
        initialViewState={initialViewState}
        onLoad={() => {
          setReady(true);
        }}
        mapStyle={MAP_STYLE}
        interactive={interactive}
        style={{ position: "absolute", inset: 0 }}
      >
        <NavigationControl position="top-right" showCompass={false} />
        {interactive ? <ScaleControl position="bottom-left" /> : null}
        {plotted.map((place) => {
          const Icon = KIND_ICON[place.kind];
          const selected = place.id === activeId;
          const showLabel = labels === "all" || selected;
          const pin = (
            <>
              {showLabel ? (
                <span
                  className={cn(
                    "max-w-[168px] truncate rounded-md px-1.5 py-0.5 text-[10px] font-bold shadow-xs",
                    selected ? "bg-secondary text-secondary-text-on" : "bg-surface/95 text-text-primary",
                  )}
                >
                  {place.name}
                </span>
              ) : null}
              <span
                className={cn(
                  "flex items-center justify-center shadow-brand-md transition-transform",
                  KIND_PIN_CLASS[place.kind],
                  selected ? "size-10 rounded-full ring-3 ring-secondary" : "size-8 rounded-xl",
                )}
              >
                <Icon size={selected ? 18 : 15} aria-hidden="true" />
              </span>
            </>
          );
          return (
            <Marker
              key={place.id}
              latitude={place.latitude}
              longitude={place.longitude}
              anchor="bottom"
              style={{ zIndex: selected ? 3 : 1 }}
            >
              {onSelect ? (
                <button
                  type="button"
                  onClick={() => {
                    onSelect(place.id);
                  }}
                  aria-label={t("map.pinLabel", { name: place.name })}
                  aria-pressed={selected}
                  className="flex flex-col items-center gap-1"
                >
                  {pin}
                </button>
              ) : (
                <span className="flex flex-col items-center gap-1" role="img" aria-label={place.name}>
                  {pin}
                </span>
              )}
            </Marker>
          );
        })}
        {userLocation ? (
          <Marker
            latitude={userLocation.latitude}
            longitude={userLocation.longitude}
            anchor="center"
            style={{ zIndex: 2 }}
          >
            <span
              className="relative flex size-7 items-center justify-center rounded-full border-2 border-white bg-primary/20 ring-8 ring-primary/15"
              role="img"
              aria-label={t("map.currentLocation")}
            >
              <span className="size-3 rounded-full border-2 border-white bg-primary shadow-sm" />
            </span>
          </Marker>
        ) : null}
      </Map>

      {onLocate ? (
        <button
          type="button"
          onClick={onLocate}
          disabled={isLocating}
          aria-label={t("map.recenter")}
          title={t("map.recenter")}
          className="absolute right-2.5 top-[84px] z-10 flex size-[29px] items-center justify-center rounded-[4px] bg-surface text-primary-dark shadow-[0_0_0_2px_rgb(0_0_0/0.1)] transition-colors hover:bg-chip-bg disabled:cursor-wait disabled:opacity-60"
        >
          <Crosshair size={16} aria-hidden="true" />
        </button>
      ) : null}

      {children}
    </div>
  );
}
