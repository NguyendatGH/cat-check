import { describe, expect, it } from "vitest";
import { emptyProductForm, formToPayload, validateProductForm } from "./productForm";

const valid = {
  ...emptyProductForm(),
  sku: "pad-01",
  name: "Cát",
  description: "Mô tả",
  priceVnd: "100000",
};

describe("validateProductForm", () => {
  it("chấp nhận form hợp lệ và tự upper-case SKU", () => {
    expect(validateProductForm(valid)).toEqual({});
    expect(formToPayload(valid).sku).toBe("PAD-01");
    expect(formToPayload(valid).imageUrl).toBeNull();
  });
  it("từ chối SKU sai, giá âm/thập phân, giá gốc <= giá bán, ảnh không phải http", () => {
    const errors = validateProductForm({
      ...valid,
      sku: "a",
      priceVnd: "-1",
      compareAtPriceVnd: "abc",
      imageUrl: "ftp://x",
      stockQuantity: "1.5",
    });
    expect(Object.keys(errors).sort()).toEqual(["compareAtPriceVnd", "imageUrl", "priceVnd", "sku", "stockQuantity"]);
    expect(validateProductForm({ ...valid, compareAtPriceVnd: "100000" }).compareAtPriceVnd).toBe(true);
    expect(validateProductForm({ ...valid, compareAtPriceVnd: "120000" })).toEqual({});
  });
});
