import { describe, expect, it } from "vitest";
import type { AdminProduct } from "./types";
import { adminProductEtag, emptyProductForm, formToPayload, productToForm, validateProductForm } from "./productForm";

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

describe("productToForm", () => {
  it("sản phẩm không có giá gốc (server bỏ hẳn key, không gửi null) ra ô giá gốc trống và form hợp lệ", () => {
    // JSON thật từ backend (`default-property-inclusion: non_null`): không có `compareAtPriceVnd`/`imageUrl`.
    const fromServer = {
      id: "p1",
      sku: "PAD-01",
      name: "Cát",
      description: "Mô tả",
      priceVnd: 100000,
      stockQuantity: 3,
      status: "DRAFT",
      updatedAt: "2026-10-01T00:00:00Z",
    } as unknown as AdminProduct;
    const form = productToForm(fromServer);
    expect(form.compareAtPriceVnd).toBe("");
    expect(validateProductForm(form)).toEqual({});
  });
});

describe("adminProductEtag", () => {
  it('khớp AdminETag.of của backend: W/"<epoch ms>-<id>", cắt phần dưới mili-giây như Instant.toEpochMilli()', () => {
    expect(adminProductEtag({ id: "p1", updatedAt: "2026-10-01T03:04:05.678901Z" })).toBe(
      `W/"${String(Date.UTC(2026, 9, 1, 3, 4, 5, 678))}-p1"`,
    );
    expect(adminProductEtag({ id: "p2", updatedAt: "1970-01-01T00:00:00Z" })).toBe('W/"0-p2"');
  });
});
