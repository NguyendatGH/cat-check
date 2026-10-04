import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME } from "@/shared/config/constants";

/**
 * Fetch wrapper cục bộ của `features/admin` — cùng quy ước `features/settings/api.ts`
 * (base `/api/v1`, `credentials: include`, CSRF header, lỗi RFC 9457 → `ApiError`).
 * Mỗi feature tự giữ wrapper vì ESLint `boundaries` cấm feature import feature.
 *
 * Khác biệt duy nhất so với wrapper của feature khác: khu vực quản trị cần **ETag**.
 * L30/L31/L37 (p8 §8.4.12) bắt buộc `If-Match`; không gửi ⇒ server từ chối. Nên wrapper
 * này trả cả header `ETag` chứ không chỉ body.
 *
 * ---------------------------------------------------------------------------
 * VỀ `reason` (p17 §17.3.8 AD10/AD11)
 * ---------------------------------------------------------------------------
 * p8 §8.4.12 đánh dấu `Rsn` cho L30, L31, L32, L37, L44. Hiện tại **chỉ** L44
 * (`PublishCareTipRequest.reason`, `@Size(min = 10)`) đã có trường này trong DTO backend;
 * bốn endpoint bảng màu còn lại chưa gắn `Rsn`/`Aud` — javadoc của
 * `AdminColorChartController` ghi rõ "chưa gắn Aud và S1:TOTP — thuộc W3".
 *
 * Cách xử lý ở FE: **luôn** bắt nhập `reason` (tối thiểu
 * {@link ADMIN_REASON_MIN_LENGTH} ký tự, đúng AD11) và **luôn** gửi kèm trong body. Với
 * L44 server lưu thật; với bốn endpoint kia Jackson bỏ qua trường lạ
 * (`fail-on-unknown-properties` mặc định tắt, đã kiểm tra `application.yml` không bật lại)
 * nên request vẫn hợp lệ, và khi W3 nối `Rsn` thì FE không phải sửa.
 * KHÔNG tự chế header riêng cho việc này — đó sẽ là bịa hợp đồng API.
 */
const BASE_URL = "/api/v1";

/** AD11 — `reason` dưới 10 ký tự hoặc toàn khoảng trắng bị coi là thiếu. */
export const ADMIN_REASON_MIN_LENGTH = 10;

/** True khi `reason` đủ điều kiện gửi lên (p17 AD10/AD11). */
export function isReasonValid(reason: string): boolean {
  return reason.trim().length >= ADMIN_REASON_MIN_LENGTH;
}

interface ProblemDetail {
  detail?: string;
  /** Backend set đúng field này (`setProperty("errorCode", …)`), KHÔNG phải `code`. */
  errorCode?: string;
}

function readCsrfToken(): string | null {
  const match = new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`).exec(document.cookie);
  const raw = match?.[1];
  return raw !== undefined && raw !== "" ? decodeURIComponent(raw) : null;
}

export interface ApiEnvelope<T> {
  data: T;
  /** Giá trị header `ETag`, dùng lại nguyên văn cho `If-Match` ở lần ghi kế tiếp. */
  etag: string | null;
}

export interface AdminRequestInit extends Omit<RequestInit, "body"> {
  /** Body dạng object — wrapper tự `JSON.stringify` và set `Content-Type`. */
  json?: unknown;
  /** Gửi `If-Match` (L30/L31/L37). `null`/rỗng thì bỏ qua. */
  ifMatch?: string | null;
}

/** Gọi API admin, trả cả body lẫn `ETag`. */
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
      // body không phải JSON — vẫn ném ApiError với status gốc
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

/** Như {@link adminRequest} nhưng chỉ lấy body — dùng khi endpoint không có ETag. */
export async function adminFetch<T>(path: string, options: AdminRequestInit = {}): Promise<T> {
  return (await adminRequest<T>(path, options)).data;
}

/** Tải CSV mã thô — response là binary và luôn phải dùng `no-store` ở server. */
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
      // body không phải JSON — vẫn ném lỗi với status gốc
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  return response.blob();
}

/** Dựng query string, bỏ qua tham số rỗng/undefined. */
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

/**
 * Kiểm tra `#RRGGBB` — khớp đúng `@Pattern` của `PointInput.displayHex`/`hexSrgb`.
 *
 * Hàm sống ở file `.ts` CỐ Ý: ESLint cấm literal hex trong `.tsx`, và biểu thức chính
 * quy này (dù không phải một màu) không nên nằm trong file component.
 */
export function isHexColor(value: string): boolean {
  return /^#[0-9A-Fa-f]{6}$/.test(value);
}
