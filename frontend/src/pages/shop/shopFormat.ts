import type { CartApi, CartLineApi, OrderApi } from "@/features/shop";
import { formatNumber } from "@/shared/lib/format/formatNumber";

export const SHOP_PAYMENT_METHOD_IDS = ["COD", "PAYOS"] as const;

export type ShopPaymentMethodId = (typeof SHOP_PAYMENT_METHOD_IDS)[number];

// Includes retired methods so old orders still render.
export const SHOP_ORDER_PAYMENT_METHODS = ["COD", "PAYOS", "MOMO", "BANK_TRANSFER"] as const;

export type ShopOrderPaymentMethod = (typeof SHOP_ORDER_PAYMENT_METHODS)[number];

export function isShopOrderPaymentMethod(value: string): value is ShopOrderPaymentMethod {
  return (SHOP_ORDER_PAYMENT_METHODS as readonly string[]).includes(value);
}

export function nextStepAfterOrder(order: OrderApi): { kind: "redirect"; url: string } | { kind: "order"; path: string } {
  if (order.paymentMethod === "PAYOS" && order.checkoutUrl) return { kind: "redirect", url: order.checkoutUrl };
  return { kind: "order", path: `/orders/${order.id}` };
}

export const SHOP_ORDER_STAGES = ["PENDING_PAYMENT", "PAID", "PACKING", "SHIPPING", "DELIVERED"] as const;

export const CART_LINE_MAX_QUANTITY = 99;

export function maxOrderQuantity(stockQuantity: number): number {
  return Math.max(0, Math.min(CART_LINE_MAX_QUANTITY, stockQuantity));
}

export function exceedsStock(line: CartLineApi): boolean {
  return line.quantity > line.product.stockQuantity;
}

export function cartSavings(cart: CartApi | undefined): number {
  if (!cart) return 0;
  return cart.lines.reduce((sum, line) => {
    const compare = line.product.compareAtPriceVnd;
    if (!compare || compare <= line.product.priceVnd) return sum;
    return sum + (compare - line.product.priceVnd) * line.quantity;
  }, 0);
}

export function formatVnd(value: number): string {
  return `${formatNumber(value)}đ`;
}

export function discountPercent(priceVnd: number, compareAtPriceVnd: number | null | undefined): number {
  if (!compareAtPriceVnd || compareAtPriceVnd <= priceVnd) return 0;
  return Math.round((1 - priceVnd / compareAtPriceVnd) * 100);
}
