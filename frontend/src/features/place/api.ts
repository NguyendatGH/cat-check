import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

/**
 * Client cho `PlaceController` (`/api/v1/places`). Kiểu dữ liệu khớp 1-1 với DTO backend:
 * `PlaceResponse` (12 field), `PlaceReviewResponse` (không có danh tính người viết),
 * `PlaceBookingRequest` (`serviceCode`, `date`, `timeSlot`, `note ≤ 1000`) và
 * `PlaceReviewRequest` (`rating 1..5`, `body ≤ 2000`). Không có endpoint giờ mở cửa, ảnh,
 * bác sĩ, bảng giá hay lịch trống — UI không được dựng các khối đó.
 */
const BASE_URL = "/api/v1";

/** `kind` theo ràng buộc `ck_place_kind` của bảng `place`. */
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
  /** `CATCHECK` = bảng `place`; `OSM` = phòng khám thú y từ OpenStreetMap (`GET /places/nearby`). Thiếu ⇒ CatCheck. */
  source?: "CATCHECK" | "OSM";
}

export interface PlaceReviewApi {
  id: string;
  rating: number;
  body: string | null;
  createdAt: string;
}

/** Lỗi HTTP giữ lại status để UI phân biệt 401 / 404 / 409 (trùng khung giờ) với lỗi chung. */
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

/**
 * Phòng khám thú y thật quanh một toạ độ (`GET /places/nearby`, nguồn OpenStreetMap, backend đã
 * làm tròn toạ độ ~1 km trước khi gọi ra ngoài). Kết quả có `source: "OSM"`, `id` dạng `osm:node/…`
 * — không dùng được với `getPlace`, đặt lịch hay đánh giá.
 */
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

/** POST không có body phản hồi: booking trả 202, review trả 204. */
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
  /** `yyyy-MM-dd` (LocalDate). */
  date: string;
  /** `HH:mm`, tối đa 16 ký tự (`place_booking.time_slot`). */
  timeSlot: string;
  note?: string;
}

/** Tạo YÊU CẦU đặt lịch (`REQUESTED`), không phải lịch hẹn đã xác nhận. 409 = khung giờ đã có người gửi. */
export function createPlaceBooking(placeId: string, payload: PlaceBookingPayload) {
  return postPlace(`/places/${encodeURIComponent(placeId)}/bookings`, payload, 202);
}

/** Upsert: mỗi tài khoản một đánh giá cho mỗi cơ sở — gửi lại sẽ ghi đè đánh giá cũ. */
export function createPlaceReview(placeId: string, payload: { rating: number; body?: string }) {
  return postPlace(`/places/${encodeURIComponent(placeId)}/reviews`, payload, 204);
}
