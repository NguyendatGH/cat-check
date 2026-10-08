import type { AdminProduct, AdminProductPayload, AdminProductStatus } from "./types";

export interface ProductFormValues {
  sku: string;
  name: string;
  description: string;
  imageUrl: string;
  priceVnd: string;
  compareAtPriceVnd: string;
  stockQuantity: string;
  status: AdminProductStatus;
}

export type ProductFormErrors = Partial<Record<keyof ProductFormValues, true>>;

const SKU_PATTERN = /^[A-Z0-9][A-Z0-9-]{1,63}$/;
const INTEGER_PATTERN = /^\d{1,12}$/;

export function emptyProductForm(): ProductFormValues {
  return {
    sku: "",
    name: "",
    description: "",
    imageUrl: "",
    priceVnd: "",
    compareAtPriceVnd: "",
    stockQuantity: "0",
    status: "DRAFT",
  };
}

export function productToForm(product: AdminProduct): ProductFormValues {
  return {
    sku: product.sku,
    name: product.name,
    description: product.description,
    imageUrl: product.imageUrl ?? "",
    priceVnd: String(product.priceVnd),
    compareAtPriceVnd: product.compareAtPriceVnd === null ? "" : String(product.compareAtPriceVnd),
    stockQuantity: String(product.stockQuantity),
    status: product.status,
  };
}

function isHttpUrl(value: string): boolean {
  if (value.length > 2000) return false;
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

/** Kiểm tra form theo ràng buộc của API; trả về tập field lỗi (rỗng = hợp lệ). */
export function validateProductForm(values: ProductFormValues): ProductFormErrors {
  const errors: ProductFormErrors = {};
  if (!SKU_PATTERN.test(values.sku.trim().toUpperCase())) errors.sku = true;
  const name = values.name.trim();
  if (name.length < 1 || name.length > 180) errors.name = true;
  const description = values.description.trim();
  if (description.length < 1 || description.length > 4000) errors.description = true;
  const imageUrl = values.imageUrl.trim();
  if (imageUrl !== "" && !isHttpUrl(imageUrl)) errors.imageUrl = true;
  const priceOk = INTEGER_PATTERN.test(values.priceVnd.trim());
  if (!priceOk) errors.priceVnd = true;
  const compare = values.compareAtPriceVnd.trim();
  if (compare !== "") {
    if (!INTEGER_PATTERN.test(compare) || (priceOk && Number(compare) <= Number(values.priceVnd))) {
      errors.compareAtPriceVnd = true;
    }
  }
  if (!INTEGER_PATTERN.test(values.stockQuantity.trim())) errors.stockQuantity = true;
  return errors;
}

/** Chỉ gọi sau khi {@link validateProductForm} không có lỗi. */
export function formToPayload(values: ProductFormValues): AdminProductPayload {
  const imageUrl = values.imageUrl.trim();
  const compare = values.compareAtPriceVnd.trim();
  return {
    sku: values.sku.trim().toUpperCase(),
    name: values.name.trim(),
    description: values.description.trim(),
    imageUrl: imageUrl === "" ? null : imageUrl,
    priceVnd: Number(values.priceVnd),
    compareAtPriceVnd: compare === "" ? null : Number(compare),
    stockQuantity: Number(values.stockQuantity),
    status: values.status,
  };
}

/** Hiển thị tiền VND giống cửa hàng: "245.000đ". */
export function formatProductVnd(value: number): string {
  return `${value.toLocaleString("vi-VN")}đ`;
}
