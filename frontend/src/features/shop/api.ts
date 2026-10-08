import { ApiError } from "@/shared/api/errors";
import { currentAcceptLanguage } from "@/shared/api/acceptLanguage";
import { CSRF_COOKIE_NAME, CSRF_HEADER_NAME, IDEMPOTENCY_HEADER_NAME } from "@/shared/config/constants";

const BASE_URL = "/api/v1";
export interface ShopProductApi {
  id: string;
  sku: string;
  name: string;
  description: string;
  imageUrl: string | null;
  priceVnd: number;
  compareAtPriceVnd: number | null;
  stockQuantity: number;
}
export interface CartLineApi {
  product: ShopProductApi;
  quantity: number;
  totalVnd: number;
}
export interface CartApi {
  lines: CartLineApi[];
  subtotalVnd: number;
  shippingFeeVnd: number;
  totalVnd: number;
}
export interface OrderApi {
  id: string;
  orderCode: string;
  status: string;
  paymentMethod: string;
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
  subtotalVnd: number;
  discountVnd: number;
  shippingFeeVnd: number;
  totalVnd: number;
  /** Dòng đơn: `product.description` rỗng, `compareAtPriceVnd` null, `stockQuantity` 0 (backend không đọc lại). */
  lines: CartLineApi[];
  createdAt: string;
}

function token() {
  const match = document.cookie.match(new RegExp(`(?:^|; )${CSRF_COOKIE_NAME}=([^;]*)`));
  return match?.[1] ? decodeURIComponent(match[1]) : null;
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  headers.set("Accept-Language", currentAcceptLanguage());
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  const csrf = token();
  if (csrf) headers.set(CSRF_HEADER_NAME, csrf);
  if ((options.method ?? "GET") !== "GET") headers.set(IDEMPOTENCY_HEADER_NAME, crypto.randomUUID());
  const response = await fetch(`${BASE_URL}${path}`, { ...options, credentials: "include", headers });
  if (!response.ok) {
    let problem: { detail?: string; errorCode?: string } | undefined;
    try {
      problem = (await response.json()) as { detail?: string; errorCode?: string };
    } catch {
      /* lỗi không có JSON */
    }
    throw new ApiError(
      problem?.detail ?? `Request failed with status ${String(response.status)}`,
      response.status,
      problem?.errorCode,
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Id sản phẩm/đơn hàng là UUID. Gửi chuỗi khác lên backend thì nhận 500 (lỗi ép kiểu path
 * variable chưa được map) — nên chặn ở client và coi như "không tìm thấy".
 */
export function isShopId(value: string | undefined): value is string {
  return typeof value === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

export function listShopProducts() {
  return request<ShopProductApi[]>("/shop/products");
}
export function getShopProduct(productId: string) {
  return request<ShopProductApi>(`/shop/products/${productId}`);
}
export function getShopCart() {
  return request<CartApi>("/cart");
}
export function setShopCartLine(productId: string, quantity: number) {
  return request<CartApi>(`/cart/${productId}`, { method: "PUT", body: JSON.stringify({ quantity }) });
}
export function removeShopCartLine(productId: string) {
  return request<CartApi>(`/cart/${productId}`, { method: "DELETE" });
}
/** `DELETE /api/v1/cart` — 204, không trả body. */
export function clearShopCart() {
  return request<undefined>("/cart", { method: "DELETE" });
}
export function createShopOrder(payload: {
  paymentMethod: string;
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
}) {
  return request<OrderApi>("/orders", { method: "POST", body: JSON.stringify(payload) });
}
export function getShopOrder(orderId: string) {
  return request<OrderApi>(`/orders/${orderId}`);
}
