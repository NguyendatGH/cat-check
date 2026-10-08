import { FlaskConical, PawPrint, Siren, Store, type LucideIcon } from "lucide-react";
import type { PlaceApi } from "@/features/place";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { validCoordinates } from "./location";

/**
 * Ánh xạ thuần dữ liệu cho module Bản đồ. Mọi giá trị hiển thị phải truy về một field của
 * `PlaceResponse`; khoảng cách là phép tính từ toạ độ API + vị trí người dùng đã cấp quyền,
 * không phải dữ liệu tự đặt.
 */

/** 4 giá trị của ràng buộc `ck_place_kind` (`CLINIC | EMERGENCY | LAB | STORE`). */
export type PlaceKind = "clinic" | "emergency" | "lab" | "store";

export const PLACE_KINDS: readonly PlaceKind[] = ["clinic", "emergency", "lab", "store"];

const KIND_BY_API: Record<string, PlaceKind> = {
  CLINIC: "clinic",
  EMERGENCY: "emergency",
  LAB: "lab",
  STORE: "store",
};

export function placeKind(kind: string): PlaceKind {
  return KIND_BY_API[kind.toUpperCase()] ?? "clinic";
}

export const KIND_ICON: Record<PlaceKind, LucideIcon> = {
  clinic: PawPrint,
  emergency: Siren,
  lab: FlaskConical,
  store: Store,
};

/** Màu ghim trên bản đồ. */
export const KIND_PIN_CLASS: Record<PlaceKind, string> = {
  clinic: "bg-primary-dark text-white",
  emergency: "bg-danger text-white",
  lab: "bg-primary text-white",
  store: "bg-secondary text-secondary-text-on",
};

/** Màu chip / ô icon. */
export const KIND_CHIP_CLASS: Record<PlaceKind, string> = {
  clinic: "bg-chip-bg text-primary-dark",
  emergency: "bg-danger-bg text-danger-text",
  lab: "bg-info text-primary-dark",
  store: "bg-secondary/25 text-secondary-text-on",
};

export interface LatLng {
  latitude: number;
  longitude: number;
}

/** Khoảng cách đường chim bay (km), công thức haversine. */
export function distanceKm(from: LatLng, to: LatLng): number {
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = rad(to.latitude - from.latitude);
  const dLng = rad(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(from.latitude)) * Math.cos(rad(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** Khoá i18n + giá trị đã định dạng cho một khoảng cách: dưới 1 km làm tròn 10 m. */
export function distanceText(km: number): { key: "distance.meters" | "distance.km"; value: string } {
  if (km < 1) return { key: "distance.meters", value: formatNumber(Math.max(10, Math.round((km * 1000) / 10) * 10)) };
  return { key: "distance.km", value: formatNumber(km, { maximumFractionDigits: 1 }) };
}

/**
 * Nguồn cơ sở: `catcheck` = bảng `place` (có chi tiết, đặt lịch, đánh giá); `osm` = phòng khám
 * thú y quanh vị trí người dùng lấy từ OpenStreetMap qua `GET /places/nearby` (chỉ xem, gọi,
 * chỉ đường — không có trang chi tiết, không đặt lịch).
 */
export type PlaceSource = "catcheck" | "osm";

/** View model: đúng các field `GET /places` trả về + khoảng cách (chỉ khi có vị trí người dùng). */
export interface ViewPlace {
  id: string;
  source: PlaceSource;
  name: string;
  kind: PlaceKind;
  address: string;
  area: string;
  specialties: string[];
  badges: string[];
  rating: number;
  reviewCount: number;
  phone: string;
  latitude: number;
  longitude: number;
  distanceKm: number | null;
}

export function toViewPlace(place: PlaceApi, from: LatLng | null = null): ViewPlace {
  const hasCoordinates = validCoordinates(place.latitude, place.longitude);
  return {
    id: place.id,
    source: place.source === "OSM" ? "osm" : "catcheck",
    name: place.name,
    kind: placeKind(place.kind),
    address: place.address,
    area: place.area,
    specialties: place.specialties,
    badges: place.badges,
    rating: place.rating,
    reviewCount: place.reviewCount,
    phone: place.phone?.trim() ?? "",
    latitude: place.latitude,
    longitude: place.longitude,
    distanceKm: from && hasCoordinates ? distanceKm(from, place) : null,
  };
}

/** Link chỉ đường ngoài app — chỉ dựng khi API có toạ độ hợp lệ. */
export function directionsUrl(place: LatLng): string | null {
  if (!validCoordinates(place.latitude, place.longitude)) return null;
  return `https://www.google.com/maps/dir/?api=1&destination=${String(place.latitude)},${String(place.longitude)}`;
}

export function telHref(phone: string): string {
  return `tel:${phone.replace(/[^\d+]/g, "")}`;
}

/** Badge "24/7" do cơ sở công bố được tô màu cấp cứu; các badge khác giữ màu trung tính. */
export function isAroundTheClockBadge(badge: string): boolean {
  return badge.includes("24/7");
}
