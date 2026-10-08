import { useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { AlertTriangle, ArrowLeft, Banknote, Info, Landmark, Loader2, Lock, MapPin, ShoppingCart } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { isApiError } from "@/shared/api";
import { MoMoGlyph } from "@/shared/assets/icons/MoMoGlyph";
import { cn } from "@/shared/lib/cn";
import { EmptyState, ErrorState, Input, SkeletonLoader } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import { useCreateOrder, useShopCart } from "@/features/shop";
import {
  CHECKOUT_LIMITS,
  validateCheckout,
  type CheckoutErrors,
  type CheckoutField,
  type CheckoutFormValues,
} from "./checkoutForm";
import { usePaymentMethodStore } from "./paymentMethodStore";
import { exceedsStock, formatVnd, type ShopPaymentMethodId } from "./shopFormat";
import { ProductGlyph, ShippingFee } from "./shopUi";

/**
 * `/checkout` — Xác nhận đơn trước khi đặt (không có PNG riêng; theo khung `Giỏ hàng` + `Đặt
 * hàng thành công`).
 *
 * Thu đúng ba field `CheckoutRequest` của backend cần (`receiverName`, `receiverPhone`,
 * `shippingAddress`) + `paymentMethod` thuộc enum `COD`/`MOMO`/`BANK_TRANSFER`, kiểm tra ở
 * client trước khi gửi (xem `checkoutForm.ts`). Thanh toán trực tuyến chưa tích hợp — nói rõ.
 * Dòng hàng và tổng tiền là `GET /api/v1/cart`; lỗi `CART_EMPTY`/`STOCK_UNAVAILABLE` của máy
 * chủ được báo đúng nguyên nhân.
 */

const PAYMENT_ICONS: Record<ShopPaymentMethodId, LucideIcon | null> = {
  COD: Banknote,
  MOMO: null,
  BANK_TRANSFER: Landmark,
};

const FIELD_ORDER: CheckoutField[] = ["receiverName", "receiverPhone", "shippingAddress"];

function serverErrorKey(error: unknown): string {
  if (isApiError(error)) {
    if (error.code === "STOCK_UNAVAILABLE") return "checkout.errors.stock";
    if (error.code === "CART_EMPTY") return "checkout.errors.cartEmpty";
    if (error.status === 401) return "checkout.errors.session";
    if (error.status === 400) return "checkout.errors.validation";
  }
  return "checkout.error";
}

export function CheckoutPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const cartQuery = useShopCart();
  const createOrder = useCreateOrder();
  const paymentMethodId = usePaymentMethodStore((s) => s.paymentMethodId);
  // Tên hiển thị của tài khoản (API phiên) làm giá trị gợi ý — người dùng sửa được.
  const displayName = useSessionStore((s) => s.user?.displayName ?? "");
  const [values, setValues] = useState<CheckoutFormValues>({
    receiverName: displayName,
    receiverPhone: "",
    shippingAddress: "",
  });
  const [touched, setTouched] = useState<Partial<Record<CheckoutField, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  const addressId = useId();

  const cart = cartQuery.data;
  const lines = cart?.lines ?? [];
  const hasStockIssue = lines.some(exceedsStock);
  const errors: CheckoutErrors = validateCheckout(values);
  const visibleError = (field: CheckoutField) =>
    (submitted || touched[field]) && errors[field] ? t(errors[field]) : undefined;

  const update = (field: CheckoutField) => (value: string) => {
    setValues((prev) => ({ ...prev, [field]: value }));
  };
  const blur = (field: CheckoutField) => () => {
    setTouched((prev) => ({ ...prev, [field]: true }));
  };

  const submit = () => {
    setSubmitted(true);
    const firstInvalid = FIELD_ORDER.find((field) => errors[field]);
    if (firstInvalid) {
      formRef.current?.querySelector<HTMLElement>(`[name="${firstInvalid}"]`)?.focus();
      return;
    }
    createOrder.mutate(
      {
        paymentMethod: paymentMethodId,
        receiverName: values.receiverName.trim(),
        receiverPhone: values.receiverPhone.trim(),
        shippingAddress: values.shippingAddress.trim(),
      },
      {
        onSuccess: (order) => {
          void navigate(`/orders/${order.id}`, { replace: true });
        },
      },
    );
  };

  const MethodIcon = PAYMENT_ICONS[paymentMethodId];

  const header = (
    <div className="flex items-center justify-between gap-3">
      <h1 className="text-[22px] font-bold text-text-primary lg:text-[26px]">{t("checkout.title")}</h1>
      <Link
        to="/cart"
        className="flex items-center gap-1.5 text-[12px] font-semibold text-primary-dark hover:underline"
      >
        <ArrowLeft size={14} aria-hidden="true" />
        {t("checkout.back")}
      </Link>
    </div>
  );

  if (cartQuery.isPending) {
    return (
      <div className="flex flex-col gap-4 lg:gap-5">
        {header}
        <div className="flex flex-col gap-4 lg:flex-row lg:gap-6" aria-hidden="true">
          <SkeletonLoader className="h-[320px] flex-1 rounded-2xl" />
          <SkeletonLoader className="h-[320px] rounded-2xl lg:w-[380px]" />
        </div>
      </div>
    );
  }
  if (cartQuery.isError) {
    return (
      <div className="flex flex-col gap-4 lg:gap-5">
        {header}
        <ErrorState
          title={t("cartPage.error")}
          onRetry={() => {
            void cartQuery.refetch();
          }}
          className="rounded-2xl bg-surface shadow-brand-md"
        />
      </div>
    );
  }
  if (lines.length === 0) {
    return (
      <div className="flex flex-col gap-4 lg:gap-5">
        {header}
        <EmptyState
          icon={<ShoppingCart size={22} />}
          title={t("checkout.emptyTitle")}
          description={t("checkout.emptyBody")}
          action={
            <Link
              to="/shop"
              className="inline-flex rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
            >
              {t("cartPage.emptyCta")}
            </Link>
          }
          className="rounded-2xl bg-surface shadow-brand-md"
        />
      </div>
    );
  }

  const addressError = visibleError("shippingAddress");

  return (
    <form
      ref={formRef}
      noValidate
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
      className="flex flex-col gap-4 lg:gap-5"
    >
      {header}

      <p className="flex items-start gap-2 rounded-xl bg-info px-3.5 py-2.5 text-[12px] leading-relaxed text-info-text">
        <Info size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
        {t("checkout.mockNotice")}
      </p>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <section className="rounded-2xl bg-surface p-4 shadow-brand-md md:p-5">
            <h2 className="flex items-center gap-2 text-[15px] font-bold text-text-primary">
              <MapPin size={16} className="text-primary-dark" aria-hidden="true" />
              {t("checkout.addressLabel")}
            </h2>
            <div className="grid gap-4 pt-4 md:grid-cols-2">
              <Input
                name="receiverName"
                label={t("checkout.receiverName")}
                autoComplete="name"
                maxLength={CHECKOUT_LIMITS.receiverName}
                value={values.receiverName}
                onChange={(event) => {
                  update("receiverName")(event.target.value);
                }}
                onBlur={blur("receiverName")}
                error={visibleError("receiverName")}
                required
              />
              <Input
                name="receiverPhone"
                label={t("checkout.receiverPhone")}
                type="tel"
                inputMode="tel"
                autoComplete="tel"
                placeholder={t("checkout.phonePlaceholder")}
                maxLength={CHECKOUT_LIMITS.receiverPhone}
                value={values.receiverPhone}
                onChange={(event) => {
                  update("receiverPhone")(event.target.value);
                }}
                onBlur={blur("receiverPhone")}
                error={visibleError("receiverPhone")}
                required
              />
              <div className="flex flex-col gap-1.5 md:col-span-2">
                <label htmlFor={addressId} className="text-caption font-semibold text-text-secondary">
                  {t("checkout.shippingAddress")}
                </label>
                <textarea
                  id={addressId}
                  name="shippingAddress"
                  autoComplete="street-address"
                  placeholder={t("checkout.addressPlaceholder")}
                  maxLength={CHECKOUT_LIMITS.shippingAddress}
                  rows={3}
                  value={values.shippingAddress}
                  onChange={(event) => {
                    update("shippingAddress")(event.target.value);
                  }}
                  onBlur={blur("shippingAddress")}
                  aria-invalid={addressError ? true : undefined}
                  aria-describedby={`${addressId}-hint`}
                  required
                  className={cn(
                    "min-h-24 w-full resize-y rounded-xl border border-border-strong bg-surface px-4 py-3 text-[15px] text-text-primary placeholder:text-text-tertiary",
                    "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                    addressError && "border-2 border-danger",
                  )}
                />
                <p
                  id={`${addressId}-hint`}
                  className={`flex justify-between gap-3 text-[12px] ${addressError ? "text-danger-text" : "text-text-tertiary"}`}
                >
                  <span>{addressError ?? t("checkout.addressHint")}</span>
                  <span className="shrink-0 tabular-nums text-text-tertiary">
                    {values.shippingAddress.length}/{CHECKOUT_LIMITS.shippingAddress}
                  </span>
                </p>
              </div>
            </div>
          </section>

          <section className="rounded-2xl bg-surface p-4 shadow-brand-md md:p-5">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-[15px] font-bold text-text-primary">{t("checkout.paymentMethodLabel")}</h2>
              <Link
                to="/cart"
                className="rounded-lg px-2 py-1 text-[12px] font-semibold text-primary-dark hover:bg-background-alt"
              >
                {t("checkout.edit")}
              </Link>
            </div>
            <div className="flex items-center gap-3 pt-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background-alt text-primary-dark">
                {MethodIcon ? <MethodIcon size={18} aria-hidden="true" /> : <MoMoGlyph size={28} />}
              </span>
              <span className="min-w-0">
                <span className="block text-[14px] font-bold text-text-primary">
                  {t(`payment.methods.${paymentMethodId}.name`)}
                </span>
                <span className="block text-[12px] leading-relaxed text-text-secondary">
                  {t(`payment.methods.${paymentMethodId}.note`)}
                </span>
              </span>
            </div>
          </section>
        </div>

        <section className="w-full rounded-2xl bg-surface p-5 shadow-brand-md lg:sticky lg:top-20 lg:w-[380px] lg:shrink-0">
          <h2 className="flex items-center justify-between gap-3 text-[15px] font-bold text-text-primary">
            {t("checkout.summaryTitle")}
            <span className="rounded-full bg-chip-bg px-2.5 py-0.5 text-[11px] font-semibold text-primary-dark">
              {t("cartPage.count", { count: lines.length })}
            </span>
          </h2>
          <ul className="flex flex-col gap-3 pt-4">
            {lines.map((line) => (
              <li key={line.product.id} className="flex items-center gap-3">
                <ProductGlyph
                  sku={line.product.sku}
                  imageUrl={line.product.imageUrl}
                  className="size-12 shrink-0"
                  iconSize={18}
                />
                <div className="min-w-0 flex-1">
                  <p className="line-clamp-2 text-[13px] font-semibold leading-snug text-text-primary">
                    {line.product.name}
                  </p>
                  <p className="text-[12px] text-text-secondary">
                    {t("checkout.qtyPrice", { count: line.quantity, price: formatVnd(line.product.priceVnd) })}
                  </p>
                  {exceedsStock(line) ? (
                    <p className="flex items-start gap-1 text-[11px] font-semibold leading-snug text-danger-text">
                      <AlertTriangle size={11} className="mt-px shrink-0" aria-hidden="true" />
                      {line.product.stockQuantity <= 0
                        ? t("product.outOfStock")
                        : t("cartPanel.onlyLeft", { count: line.product.stockQuantity })}
                    </p>
                  ) : null}
                </div>
                <span className="shrink-0 text-[13px] font-bold text-primary-dark">{formatVnd(line.totalVnd)}</span>
              </li>
            ))}
          </ul>

          <dl className="mt-4 flex flex-col gap-2 border-t border-border pt-3 text-[13px]">
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("cartPanel.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(cart?.subtotalVnd ?? 0)}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("cartPanel.shippingLabel")}</dt>
              <dd className="text-right font-semibold text-text-primary">
                <ShippingFee value={cart?.shippingFeeVnd ?? 0} />
              </dd>
            </div>
          </dl>

          <div className="mt-3 flex items-end justify-between gap-4 border-t border-border pt-3">
            <span className="text-[15px] font-bold text-text-primary">{t("checkout.totalLabel")}</span>
            <span className="text-[24px] font-bold leading-none text-primary-dark">
              {formatVnd(cart?.totalVnd ?? 0)}
            </span>
          </div>

          {hasStockIssue ? (
            <p
              role="alert"
              className="mt-4 rounded-lg bg-danger-bg px-3 py-2 text-[12px] leading-relaxed text-danger-text"
            >
              {t("cartPage.stockIssue")}{" "}
              <Link to="/cart" className="font-semibold underline">
                {t("cartPanel.reviewCart")}
              </Link>
            </p>
          ) : null}

          <button
            type="submit"
            disabled={hasStockIssue || createOrder.isPending}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
          >
            {createOrder.isPending ? (
              <Loader2 size={15} className="animate-spin" aria-hidden="true" />
            ) : (
              <Lock size={15} aria-hidden="true" />
            )}
            {createOrder.isPending ? t("checkout.placing") : t("checkout.placeOrder")}
          </button>
          {createOrder.isError ? (
            <p role="alert" className="pt-2 text-center text-[12px] leading-relaxed text-danger-text">
              {t(serverErrorKey(createOrder.error))}
            </p>
          ) : null}
          {submitted && Object.keys(errors).length > 0 ? (
            <p role="alert" className="pt-2 text-center text-[12px] text-danger-text">
              {t("checkout.errors.fixFields")}
            </p>
          ) : null}
        </section>
      </div>
    </form>
  );
}
