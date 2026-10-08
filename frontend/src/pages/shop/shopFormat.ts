/**
 * Helper hiển thị + hằng số nghiệp vụ của module Cửa hàng/Giỏ hàng/Đơn hàng.
 *
 * Không chứa dữ liệu hiển thị nào: tên, giá, tồn kho, tổng tiền, trạng thái đơn đều đến từ
 * Shop API (`features/shop`). Nhãn phân loại pH đến từ `entities/ph-bands`.
 */
import type { CartApi, CartLineApi } from "@/features/shop";

/**
 * Phương thức thanh toán — khớp CHECK constraint `ck_shop_order_payment` của
 * `V20__shop.sql` (`COD`, `MOMO`, `BANK_TRANSFER`). Backend từ chối giá trị ngoài danh sách.
 */
export const SHOP_PAYMENT_METHOD_IDS = ["COD", "MOMO", "BANK_TRANSFER"] as const;

export type ShopPaymentMethodId = (typeof SHOP_PAYMENT_METHOD_IDS)[number];

export function isShopPaymentMethodId(value: string): value is ShopPaymentMethodId {
  return (SHOP_PAYMENT_METHOD_IDS as readonly string[]).includes(value);
}

/** Trạng thái đơn — khớp CHECK constraint `ck_shop_order_status` (theo thứ tự xử lý). */
export const SHOP_ORDER_STAGES = ["PENDING_PAYMENT", "PAID", "PACKING", "SHIPPING", "DELIVERED"] as const;

/** Trần số lượng mỗi dòng giỏ — `SetCartLineRequest` của backend: `@Min(1) @Max(99)`. */
export const CART_LINE_MAX_QUANTITY = 99;

/** Số lượng tối đa đặt được cho một sản phẩm: tồn kho thật, không vượt trần 99 của API. */
export function maxOrderQuantity(stockQuantity: number): number {
  return Math.max(0, Math.min(CART_LINE_MAX_QUANTITY, stockQuantity));
}

/** Dòng giỏ đặt nhiều hơn tồn kho hiện tại — máy chủ sẽ từ chối tạo đơn (`STOCK_UNAVAILABLE`). */
export function exceedsStock(line: CartLineApi): boolean {
  return line.quantity > line.product.stockQuantity;
}

/** Tổng tiết kiệm so với `compareAtPriceVnd` của từng dòng (0 khi API không trả giá so sánh). */
export function cartSavings(cart: CartApi | undefined): number {
  if (!cart) return 0;
  return cart.lines.reduce((sum, line) => {
    const compare = line.product.compareAtPriceVnd;
    if (!compare || compare <= line.product.priceVnd) return sum;
    return sum + (compare - line.product.priceVnd) * line.quantity;
  }, 0);
}

/** Định dạng tiền VND theo kiểu hiển thị của thiết kế: "245.000đ". */
export function formatVnd(value: number): string {
  return `${value.toLocaleString("vi-VN")}đ`;
}

/** Phần trăm giảm giá so với giá gốc — làm tròn về số nguyên, 0 khi không có giá so sánh. */
export function discountPercent(priceVnd: number, compareAtPriceVnd: number | null | undefined): number {
  if (!compareAtPriceVnd || compareAtPriceVnd <= priceVnd) return 0;
  return Math.round((1 - priceVnd / compareAtPriceVnd) * 100);
}
