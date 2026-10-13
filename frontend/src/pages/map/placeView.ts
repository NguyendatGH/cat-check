import { FlaskConical, PawPrint, Siren, Store, type LucideIcon } from "lucide-react";
import type { PlaceApi } from "@/features/place";
import { formatNumber } from "@/shared/lib/format/formatNumber";
import { validCoordinates } from "./location";


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

export const KIND_PIN_CLASS: Record<PlaceKind, string> = {
  clinic: "bg-primary-dark text-white",
  emergency: "bg-danger text-white",
  lab: "bg-primary text-white",
  store: "bg-secondary text-secondary-text-on",
};

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

export function distanceKm(from: LatLng, to: LatLng): number {
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = rad(to.latitude - from.latitude);
  const dLng = rad(to.longitude - from.longitude);
  const a =
    Math.sin(dLat / 2) ** 2 + Math.cos(rad(from.latitude)) * Math.cos(rad(to.latitude)) * Math.sin(dLng / 2) ** 2;
  return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

export function distanceText(km: number): { key: "distance.meters" | "distance.km"; value: string } {
  if (km < 1) return { key: "distance.meters", value: formatNumber(Math.max(10, Math.round((km * 1000) / 10) * 10)) };
  return { key: "distance.km", value: formatNumber(km, { maximumFractionDigits: 1 }) };
}

export type PlaceSource = "catcheck" | "osm";

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

export function directionsUrl(place: LatLng & { name?: string; address?: string }): string | null {
  if (!validCoordinates(place.latitude, place.longitude)) return null;
  // Include clinic name and address for better context in Google Maps
  const destination = place.name && place.address
    ? encodeURIComponent(`${place.name} ${place.address}`)
    : `${String(place.latitude)},${String(place.longitude)}`;
  return `https://www.google.com/maps/dir/?api=1&destination=${destination}`;
}

export function telHref(phone: string): string {
  return `tel:${phone.replace(/[^\d+]/g, "")}`;
}

export function isAroundTheClockBadge(badge: string): boolean {
  return badge.includes("24/7");
}
