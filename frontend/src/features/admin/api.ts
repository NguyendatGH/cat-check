import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";

const BASE_URL = "/api/v1";

export const ADMIN_REASON_MIN_LENGTH = 10;

export function isReasonValid(reason: string): boolean {
  return reason.trim().length >= ADMIN_REASON_MIN_LENGTH;
}

interface ProblemDetail {
  detail?: string;
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`).exec(document.cookie);
  const raw = match?.[1];
  return raw !== undefined && raw !== "" ? decodeURIComponent(raw) : null;
}

export interface ApiEnvelope<T> {
  data: T;
  etag: string | null;
}

export interface AdminRequestInit extends Omit<RequestInit, "body"> {
  json?: unknown;
  ifMatch?: string | null;
}

export async function adminRequest<T>(path: string, options: AdminRequestInit = {}): Promise<ApiEnvelope<T>> {
  const { json, ifMatch, ...init } = options;
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (json !== undefined) {
    headers.set("Content-Type", "application/json");
  }
  if (ifMatch !== undefined && ifMatch !== null && ifMatch !== "") {
    headers.set("If-Match", ifMatch);
  }
  const csrf = readCsrfToken();
  if (csrf !== null) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    credentials: "include",
    headers,
    ...(json === undefined ? {} : { body: JSON.stringify(json) }),
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Body lỗi không phải JSON: giữ thông điệp mặc định theo mã HTTP.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }

  const text = await response.text();
  const payload = text.length > 0 ? (JSON.parse(text) as unknown) : undefined;
  return { data: payload as T, etag: response.headers.get("ETag") };
}

export async function adminFetch<T>(path: string, options: AdminRequestInit = {}): Promise<T> {
  return normalizeOffsetPage((await adminRequest<T>(path, options)).data);
}

/**
 * Admin list endpoints disagree on the page envelope: some return `number`/`totalPages`/`hasMore`,
 * others (color chart, community reports, privacy requests) only `page` + `totalElements`.
 * Fill in the AdminOffsetPage fields so pagers never compute NaN.
 */
export function normalizeOffsetPage<T>(data: T): T {
  if (data === null || typeof data !== "object" || !Array.isArray((data as { items?: unknown }).items)) return data;
  const page = data as Record<string, unknown>;
  const isNum = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value);
  const number = isNum(page.number) ? page.number : isNum(page.page) ? page.page : 0;
  const size = isNum(page.size) && page.size > 0 ? page.size : (page.items as unknown[]).length || 1;
  const totalElements = isNum(page.totalElements) ? page.totalElements : (page.items as unknown[]).length;
  const totalPages = isNum(page.totalPages) ? page.totalPages : Math.max(1, Math.ceil(totalElements / size));
  const hasMore = typeof page.hasMore === "boolean" ? page.hasMore : number + 1 < totalPages;
  return { ...page, number, size, totalElements, totalPages, hasMore } as T;
}

export async function downloadAdminActivationBatchCsv(batchId: string, reason: string): Promise<Blob> {
  const headers = new Headers({
    Accept: "text/csv",
    "Accept-Language": currentAcceptLanguage(),
  });
  const csrf = readCsrfToken();
  if (csrf !== null) {
    headers.set(CSRF_HEADER_NAME, csrf);
  }
  const response = await fetch(
    `${BASE_URL}/admin/activation-codes/batches/${encodeURIComponent(batchId)}/csv?reason=${encodeURIComponent(reason)}`,
    { credentials: "include", headers },
  );
  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Body lỗi không phải JSON: giữ thông điệp mặc định theo mã HTTP.
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return response.blob();
}

export function adminQuery(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query.length > 0 ? `?${query}` : "";
}

export function isHexColor(value: string): boolean {
  return /^#[0-9A-Fa-f]{6}$/.test(value);
}
