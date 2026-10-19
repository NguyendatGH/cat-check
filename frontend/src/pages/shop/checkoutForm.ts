export interface CheckoutFormValues {
  receiverName: string;
  receiverPhone: string;
  shippingAddress: string;
}

export type CheckoutField = keyof CheckoutFormValues;

export type CheckoutErrors = Partial<Record<CheckoutField, string>>;

export const CHECKOUT_LIMITS = { receiverName: 120, receiverPhone: 32, shippingAddress: 500 } as const;

const MIN_ADDRESS_LENGTH = 10;

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
