import { describe, expect, it } from "vitest";
import { isValidVietnamPhone, validateCheckout } from "./checkoutForm";
import { cartSavings, exceedsStock, maxOrderQuantity } from "./shopFormat";
import type { CartApi, CartLineApi } from "@/features/shop";

const valid = {
  receiverName: "Nguyễn An",
  receiverPhone: "0901 234 567",
  shippingAddress: "12 Lê Lợi, phường Hải Châu, Đà Nẵng",
};

describe("validateCheckout", () => {
  it("accepts a complete Vietnamese shipping form", () => {
    expect(validateCheckout(valid)).toEqual({});
  });

  it("requires every field", () => {
    expect(validateCheckout({ receiverName: " ", receiverPhone: "", shippingAddress: "" })).toEqual({
      receiverName: "checkout.errors.nameRequired",
      receiverPhone: "checkout.errors.phoneRequired",
      shippingAddress: "checkout.errors.addressRequired",
    });
  });

  it("rejects malformed phones and too-short addresses", () => {
    const errors = validateCheckout({ ...valid, receiverPhone: "12345", shippingAddress: "Đà Nẵng" });
    expect(errors.receiverPhone).toBe("checkout.errors.phoneInvalid");
    expect(errors.shippingAddress).toBe("checkout.errors.addressTooShort");
  });

  it("enforces the backend length limits", () => {
    const errors = validateCheckout({ ...valid, receiverName: "a".repeat(121), shippingAddress: "b".repeat(501) });
    expect(errors.receiverName).toBe("checkout.errors.nameTooLong");
    expect(errors.shippingAddress).toBe("checkout.errors.addressTooLong");
  });

  it("recognises common phone spellings", () => {
    expect(isValidVietnamPhone("0901234567")).toBe(true);
    expect(isValidVietnamPhone("+84 901 234 567")).toBe(true);
    expect(isValidVietnamPhone("090.123.4567")).toBe(true);
    expect(isValidVietnamPhone("abc")).toBe(false);
  });
});

function line(
  quantity: number,
  stockQuantity: number,
  priceVnd = 100000,
  compareAtPriceVnd: number | null = 120000,
): CartLineApi {
  return {
    product: {
      id: "p",
      sku: "SKU",
      name: "Sản phẩm",
      description: "",
      imageUrl: null,
      priceVnd,
      compareAtPriceVnd,
      stockQuantity,
    },
    quantity,
    totalVnd: priceVnd * quantity,
  };
}

describe("cart helpers", () => {
  it("caps orderable quantity at stock and the API maximum of 99", () => {
    expect(maxOrderQuantity(0)).toBe(0);
    expect(maxOrderQuantity(5)).toBe(5);
    expect(maxOrderQuantity(500)).toBe(99);
  });

  it("flags lines that exceed current stock", () => {
    expect(exceedsStock(line(2, 5))).toBe(false);
    expect(exceedsStock(line(6, 5))).toBe(true);
    expect(exceedsStock(line(1, 0))).toBe(true);
  });

  it("derives savings only from compareAtPriceVnd", () => {
    const cart: CartApi = {
      lines: [line(2, 10), line(1, 10, 50000, null)],
      subtotalVnd: 250000,
      shippingFeeVnd: 30000,
      totalVnd: 280000,
    };
    expect(cartSavings(cart)).toBe(40000);
    expect(cartSavings(undefined)).toBe(0);
  });
});
