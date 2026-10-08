/**
 * Kiểm tra form nhận hàng ở `/checkout` trước khi gọi `POST /api/v1/orders`.
 *
 * Trần độ dài khớp `CheckoutRequest` của backend (`@NotBlank @Size(max = 120)` tên,
 * `@Size(max = 32)` số điện thoại, `@Size(max = 500)` địa chỉ). Số điện thoại kiểm theo dạng
 * Việt Nam (0xxxxxxxxx hoặc +84xxxxxxxxx, 9–10 chữ số sau đầu số) vì đơn giao trong nước.
 */
export interface CheckoutFormValues {
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
}

export type CheckoutField = keyof CheckoutFormValues;

/** Khoá i18n (namespace `shop`) của lỗi từng ô; ô hợp lệ thì không có mặt. */
export type CheckoutErrors = Partial<Record<CheckoutField, string>>;

export const CHECKOUT_LIMITS = { receiverName: 120, receiverPhone: 32, shippingAddress: 500 } as const;

const MIN_ADDRESS_LENGTH = 10;

/** Bỏ khoảng trắng, dấu chấm, gạch nối, ngoặc mà người dùng hay gõ khi nhập số điện thoại. */
export function normalizePhone(value: string): string {
  return value.replace(/[\s.\-()]/g, "");
}

export function isValidVietnamPhone(value: string): boolean {
  return /^(?:\+?84|0)\d{9,10}$/.test(normalizePhone(value));
}

export function validateCheckout(values: CheckoutFormValues): CheckoutErrors {
  const errors: CheckoutErrors = {};
  const name = values.receiverName.trim();
  const phone = values.receiverPhone.trim();
  const address = values.shippingAddress.trim();

  if (!name) errors.receiverName = "checkout.errors.nameRequired";
  else if (name.length > CHECKOUT_LIMITS.receiverName) errors.receiverName = "checkout.errors.nameTooLong";

  if (!phone) errors.receiverPhone = "checkout.errors.phoneRequired";
  else if (phone.length > CHECKOUT_LIMITS.receiverPhone || !isValidVietnamPhone(phone))
    errors.receiverPhone = "checkout.errors.phoneInvalid";

  if (!address) errors.shippingAddress = "checkout.errors.addressRequired";
  else if (address.length < MIN_ADDRESS_LENGTH) errors.shippingAddress = "checkout.errors.addressTooShort";
  else if (address.length > CHECKOUT_LIMITS.shippingAddress) errors.shippingAddress = "checkout.errors.addressTooLong";

  return errors;
}
