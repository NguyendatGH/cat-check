import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

const BASE_URL = "/api/v1";

export type PlaceKindApi = "CLINIC" | "EMERGENCY" | "LAB" | "STORE";

export interface PlaceApi {
  id: string;
  name: string;
  kind: string;
  address: string;
  area: string;
  latitude: number;
  longitude: number;
  phone: string | null;
  specialties: string[];
  badges: string[];
  rating: number;
  reviewCount: number;
  source?: "CATCHECK" | "OSM";
}

export interface PlaceReviewApi {
  id: string;
  rating: number;
  body: string | null;
  createdAt: string;
}

export class PlaceRequestError extends Error {
  readonly status: number;
  constructor(status: number) {
    super(`Place request failed: ${String(status)}`);
    this.name = "PlaceRequestError";
    this.status = status;
  }
}

export function placeErrorStatus(error: unknown): number | null {
  return error instanceof PlaceRequestError ? error.status : null;
}

async function getJson<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() },
    signal,
  });
  if (!response.ok) throw new PlaceRequestError(response.status);
  return (await response.json()) as T;
}

export function listPlaces(
  params: { query?: string; kind?: string; area?: string; latitude?: number; longitude?: number } = {},
  signal?: AbortSignal,
) {
  const query = new URLSearchParams();
  if (params.query) query.set("query", params.query);
  if (params.kind) query.set("kind", params.kind);
  if (params.area) query.set("area", params.area);
  if (params.latitude !== undefined) query.set("latitude", String(params.latitude));
  if (params.longitude !== undefined) query.set("longitude", String(params.longitude));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return getJson<PlaceApi[]>(`/places${suffix}`, signal);
}

export function listNearbyPlaces(
  params: { latitude: number; longitude: number; radiusKm?: number },
  signal?: AbortSignal,
) {
  const query = new URLSearchParams({
    latitude: String(params.latitude),
    longitude: String(params.longitude),
  });
  if (params.radiusKm !== undefined) query.set("radiusKm", String(params.radiusKm));
  return getJson<PlaceApi[]>(`/places/nearby?${query.toString()}`, signal);
}

export function getPlace(placeId: string, signal?: AbortSignal) {
  return getJson<PlaceApi>(`/places/${encodeURIComponent(placeId)}`, signal);
}

export function listPlaceReviews(placeId: string, limit = 20, signal?: AbortSignal): Promise<PlaceReviewApi[]> {
  const query = new URLSearchParams({ limit: String(limit) });
  return getJson<PlaceReviewApi[]>(`/places/${encodeURIComponent(placeId)}/reviews?${query.toString()}`, signal);
}

function csrfToken(): string | null {
  const match = new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`).exec(document.cookie);
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

async function postPlace(path: string, body: unknown, acceptedStatus: 202 | 204): Promise<void> {
  const headers = new Headers({
    Accept: "application/json",
    "Accept-Language": currentAcceptLanguage(),
    "Content-Type": "application/json",
    [IDEMPOTENCY_HEADER_NAME]: crypto.randomUUID(),
  });
  const csrf = csrfToken();
  if (csrf) headers.set(CSRF_HEADER_NAME, csrf);
  const response = await fetch(`${BASE_URL}${path}`, {
    method: "POST",
    credentials: "include",
    headers,
    body: JSON.stringify(body),
  });
  if (response.status !== acceptedStatus) throw new PlaceRequestError(response.status);
}

export interface PlaceBookingPayload {
  serviceCode: string;
  date: string;
  timeSlot: string;
  note?: string;
}

export function createPlaceBooking(placeId: string, payload: PlaceBookingPayload) {
  return postPlace(`/places/${encodeURIComponent(placeId)}/bookings`, payload, 202);
}

export function createPlaceReview(placeId: string, payload: { rating: number; body?: string }) {
  return postPlace(`/places/${encodeURIComponent(placeId)}/reviews`, payload, 204);
}
