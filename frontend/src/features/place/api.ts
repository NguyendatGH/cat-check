import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

const BASE_URL = "/api/v1";

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
}

export interface PlaceReviewApi {
  id: string;
  rating: number;
  body: string | null;
  createdAt: string;
}

export function listPlaces(params: { query?: string; kind?: string; area?: string; latitude?: number; longitude?: number } = {}) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) if (value !== "") query.set(key, String(value));
  const suffix = query.toString() ? `?${query.toString()}` : "";
  return fetch(`${BASE_URL}/places${suffix}`, { headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() } }).then(async (response) => {
    if (!response.ok) throw new Error(`Place request failed: ${String(response.status)}`);
    return (await response.json()) as PlaceApi[];
  });
}

export function getPlace(placeId: string) {
  return fetch(`${BASE_URL}/places/${placeId}`, { headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() } }).then(async (response) => {
    if (!response.ok) throw new Error(`Place request failed: ${String(response.status)}`);
    return (await response.json()) as PlaceApi;
  });
}

export function listPlaceReviews(placeId: string, limit = 20): Promise<PlaceReviewApi[]> {
  const query = new URLSearchParams({ limit: String(limit) });
  return fetch(`${BASE_URL}/places/${encodeURIComponent(placeId)}/reviews?${query}`, {
    headers: { Accept: "application/json", "Accept-Language": currentAcceptLanguage() },
  }).then(async (response) => {
    if (!response.ok) throw new Error(`Place reviews request failed: ${String(response.status)}`);
    return (await response.json()) as PlaceReviewApi[];
  });
}

function csrfToken(): string | null {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

async function postPlace<T>(path: string, body: unknown, acceptedStatus = 200): Promise<T | undefined> {
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
  if (response.status !== acceptedStatus) {
    throw new Error(`Place request failed: ${String(response.status)}`);
  }
  return response.status === 204 || response.status === 202 ? undefined : (await response.json()) as T;
}

export interface PlaceBookingPayload {
  serviceCode: string;
  date: string;
  timeSlot: string;
  note?: string;
}

export function createPlaceBooking(placeId: string, payload: PlaceBookingPayload) {
  return postPlace<never>(`/places/${encodeURIComponent(placeId)}/bookings`, payload, 202);
}

export function createPlaceReview(placeId: string, payload: { rating: number; body?: string }) {
  return postPlace<never>(`/places/${encodeURIComponent(placeId)}/reviews`, payload, 204);
}
