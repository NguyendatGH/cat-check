import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  AlertTriangle,
  ArrowLeft,
  Loader2,
  Lock,
  MapPin,
  Navigation,
  ShoppingCart,
  Tag,
  Ticket,
  X,
} from "lucide-react";
import { isApiError } from "@/shared/api";
import { cn } from "@/shared/lib/cn";
import { EmptyState, ErrorState, Input, SkeletonLoader, toast } from "@/shared/ui";
import { useSessionStore } from "@/entities/user";
import { useCreateOrder, useShopCart, useShopPromos } from "@/features/shop";
import {
  CHECKOUT_LIMITS,
  validateCheckout,
  type CheckoutErrors,
  type CheckoutField,
  type CheckoutFormValues,
} from "./checkoutForm";
import { usePaymentMethodStore } from "./paymentMethodStore";
import { PaymentMethodPicker } from "./PaymentMethodPicker";
import { exceedsStock, formatVnd, nextStepAfterOrder } from "./shopFormat";
import { ProductGlyph, ShippingFee } from "./shopUi";

export interface PromoCodeDef {
  code: string;
  label: string;
  type: "PERCENT" | "FIXED" | "SHIPPING";
  amount: number;
}

export const PROMO_LIST: PromoCodeDef[] = [
  { code: "CATCHECK10", label: "Giảm 10%", type: "PERCENT", amount: 10 },
  { code: "FREESHIP", label: "Giảm 30.000đ ship", type: "SHIPPING", amount: 30000 },
  { code: "CATFRIEND", label: "Giảm 20.000đ", type: "FIXED", amount: 20000 },
];

export const PROMO_CODES: Record<string, PromoCodeDef> = Object.fromEntries(PROMO_LIST.map((p) => [p.code, p]));

const SAVED_CHECKOUT_KEY = "catcheck_saved_shipping_info";

// Nháp đang điền (kể cả mã giảm giá) sống qua lần tải lại trang, mất khi đóng tab; khác với "lưu cho lần sau".
const CHECKOUT_DRAFT_KEY = "catcheck_checkout_draft";

type CheckoutDraft = CheckoutFormValues & { promoCode?: string };

function readStored<T>(storage: () => Storage, key: string): Partial<T> | null {
  try {
    const raw = storage().getItem(key);
    return raw ? (JSON.parse(raw) as Partial<T>) : null;
  } catch {
    return null;
  }
}

function initialValues(displayName: string): CheckoutFormValues {
  const draft = readStored<CheckoutDraft>(() => sessionStorage, CHECKOUT_DRAFT_KEY);
  if (draft) {
    return {
      receiverName: draft.receiverName ?? displayName,
      receiverPhone: draft.receiverPhone ?? "",
      shippingAddress: draft.shippingAddress ?? "",
    };
  }
  const saved = readStored<CheckoutFormValues>(() => localStorage, SAVED_CHECKOUT_KEY);
  return {
    receiverName: saved?.receiverName || displayName,
    receiverPhone: saved?.receiverPhone || "",
    shippingAddress: saved?.shippingAddress || "",
  };
}

const FIELD_ORDER: CheckoutField[] = ["receiverName", "receiverPhone", "shippingAddress"];

function serverErrorKey(error: unknown): string {
  if (isApiError(error)) {
    if (error.code === "STOCK_UNAVAILABLE") return "checkout.errors.stock";
    if (error.code === "CART_EMPTY") return "checkout.errors.cartEmpty";
    if (error.code === "PAYMENT_UNAVAILABLE") return "checkout.errors.paymentUnavailable";
    if (error.code === "ORDER_LIMIT_REACHED") return "checkout.errors.orderLimit";
    if (error.code === "PROMO_CODE_INVALID") return "checkout.promoInvalid";
    if (error.status === 401) return "checkout.errors.session";
    if (error.status === 400) return "checkout.errors.validation";
  }
  return "checkout.error";
}

export function CheckoutPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const cartQuery = useShopCart();
  const promosQuery = useShopPromos();
  const createOrder = useCreateOrder();
  const paymentMethodId = usePaymentMethodStore((s) => s.paymentMethodId);
  const displayName = useSessionStore((s) => s.user?.displayName ?? "");
  const [values, setValues] = useState<CheckoutFormValues>(() => initialValues(displayName));
  const [touched, setTouched] = useState<Partial<Record<CheckoutField, boolean>>>({});
  const [submitted, setSubmitted] = useState(false);
  const [isLocating, setIsLocating] = useState(false);
  const [promoInput, setPromoInput] = useState("");
  const [appliedPromo, setAppliedPromo] = useState<PromoCodeDef | null>(() => {
    const code = readStored<CheckoutDraft>(() => sessionStorage, CHECKOUT_DRAFT_KEY)?.promoCode;
    return (code && PROMO_CODES[code]) || null;
  });
  const [promoError, setPromoError] = useState<string | null>(null);

  const availablePromos = (promosQuery.data && promosQuery.data.length > 0) ? promosQuery.data : PROMO_LIST;

  useEffect(() => {
    const draftCode = readStored<CheckoutDraft>(() => sessionStorage, CHECKOUT_DRAFT_KEY)?.promoCode;
    if (draftCode && !appliedPromo && promosQuery.data) {
      const match = promosQuery.data.find((p) => p.code.toUpperCase() === draftCode.toUpperCase());
      if (match) {
        setAppliedPromo(match);
      }
    }
  }, [promosQuery.data, appliedPromo]);
  const [saveInfo, setSaveInfo] = useState(() => {
    try {
      return localStorage.getItem(SAVED_CHECKOUT_KEY) !== null;
    } catch {
      return false;
    }
  });

  const formRef = useRef<HTMLFormElement>(null);
  const addressId = useId();

  useEffect(() => {
    try {
      const draft: CheckoutDraft = { ...values, promoCode: appliedPromo?.code };
      sessionStorage.setItem(CHECKOUT_DRAFT_KEY, JSON.stringify(draft));
    } catch {
      // Ignore storage errors
    }
  }, [values, appliedPromo]);

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

  const handleApplyPromo = () => {
    const clean = promoInput.trim().toUpperCase();
    if (!clean) return;
    const found = availablePromos.find((p) => p.code.toUpperCase() === clean);
    if (found) {
      setAppliedPromo(found);
      setPromoError(null);
      toast.success(t("checkout.promoApplied", { code: found.code }));
    } else {
      setPromoError(t("checkout.promoInvalid"));
    }
  };

  const handleGetCurrentLocation = () => {
    const geolocation = "geolocation" in navigator ? navigator.geolocation : undefined;
    if (!geolocation) {
      toast.error(t("checkout.locationError"));
      return;
    }
    setIsLocating(true);
    geolocation.getCurrentPosition(
      (pos) => {
        void (async () => {
          const { latitude, longitude } = pos.coords;
          try {
            const res = await fetch(
              `https://nominatim.openstreetmap.org/reverse?format=json&lat=${String(latitude)}&lon=${String(longitude)}&zoom=18&addressdetails=1`,
              { headers: { "Accept-Language": "vi,en" } },
            );
            if (res.ok) {
              const data = (await res.json()) as { display_name?: string };
              if (data.display_name) {
                update("shippingAddress")(data.display_name);
                setTouched((prev) => ({ ...prev, shippingAddress: true }));
                setIsLocating(false);
                return;
              }
            }
          } catch {
            // If network or geocoding fails, fallback to coordinates
          }
          const coordsText = `${latitude.toFixed(4)}° N, ${longitude.toFixed(4)}° E (Vị trí hiện tại)`;
          update("shippingAddress")(coordsText);
          setTouched((prev) => ({ ...prev, shippingAddress: true }));
          setIsLocating(false);
        })();
      },
      () => {
        setIsLocating(false);
        toast.error(t("checkout.locationError"));
      },
      { timeout: 10000, enableHighAccuracy: true },
    );
  };

  const subtotal = cart?.subtotalVnd ?? 0;
  const shippingFee = cart?.shippingFeeVnd ?? 0;
  let promoDiscountVnd = 0;
  if (appliedPromo) {
    if (appliedPromo.type === "PERCENT") {
      promoDiscountVnd = Math.round((subtotal * appliedPromo.amount) / 100);
    } else if (appliedPromo.type === "SHIPPING") {
      promoDiscountVnd = Math.min(shippingFee, appliedPromo.amount);
    } else {
      promoDiscountVnd = Math.min(subtotal + shippingFee, appliedPromo.amount);
    }
  }
  const finalTotalVnd = Math.max(0, subtotal + shippingFee - promoDiscountVnd);

  const submit = () => {
    setSubmitted(true);
    const firstInvalid = FIELD_ORDER.find((field) => errors[field]);
    if (firstInvalid) {
      formRef.current?.querySelector<HTMLElement>(`[name="${firstInvalid}"]`)?.focus();
      return;
    }

    // Persist or clear shipping information according to saveInfo checkbox
    if (saveInfo) {
      try {
        localStorage.setItem(
          SAVED_CHECKOUT_KEY,
          JSON.stringify({
            receiverName: values.receiverName.trim(),
            receiverPhone: values.receiverPhone.trim(),
            shippingAddress: values.shippingAddress.trim(),
          }),
        );
      } catch {
        // Ignore storage errors
      }
    } else {
      try {
        localStorage.removeItem(SAVED_CHECKOUT_KEY);
      } catch {
        // Ignore storage errors
      }
    }

    createOrder.mutate(
      {
        paymentMethod: paymentMethodId,
        receiverName: values.receiverName.trim(),
        receiverPhone: values.receiverPhone.trim(),
        shippingAddress: values.shippingAddress.trim(),
        promoCode: appliedPromo?.code,
      },
      {
        onSuccess: (order) => {
          try {
            sessionStorage.removeItem(CHECKOUT_DRAFT_KEY);
          } catch {
            // Ignore storage errors
          }
          const next = nextStepAfterOrder(order);
          if (next.kind === "redirect") window.location.assign(next.url);
          else void navigate(next.path, { replace: true, state: { justPlaced: true } });
        },
      },
    );
  };

  const header = (
    <div className="flex items-center justify-between gap-3">
      <h1 className="text-[22px] font-bold text-text-primary lg:text-[26px]">{t("checkout.title")}</h1>
      {/* Mobile: TaskLayout đã có nút quay lại ở header. */}
      <Link
        to="/cart"
        className="hidden items-center gap-1.5 text-[12px] font-semibold text-primary-dark hover:underline lg:flex"
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
                <div className="flex items-center justify-between">
                  <label htmlFor={addressId} className="text-caption font-semibold text-text-secondary">
                    {t("checkout.shippingAddress")}
                  </label>
                  <button
                    type="button"
                    onClick={handleGetCurrentLocation}
                    disabled={isLocating}
                    className="inline-flex items-center gap-1.5 text-[12px] font-semibold text-primary transition-colors hover:text-primary-dark disabled:opacity-60"
                  >
                    <Navigation size={13} className={cn("shrink-0", isLocating && "animate-spin")} aria-hidden="true" />
                    <span>{isLocating ? t("checkout.locatingAddress") : t("checkout.useCurrentLocation")}</span>
                  </button>
                </div>
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

                <label className="mt-2 flex items-center gap-2 text-[13px] text-text-primary cursor-pointer select-none">
                  <input
                    type="checkbox"
                    checked={saveInfo}
                    onChange={(e) => {
                      setSaveInfo(e.target.checked);
                    }}
                    className="size-4 rounded border-border-strong text-primary accent-primary focus:ring-primary/20"
                  />
                  <span>{t("checkout.saveInfoLabel")}</span>
                </label>
              </div>
            </div>
          </section>

          <PaymentMethodPicker title={t("checkout.paymentMethodLabel")} />
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

          {/* Promo code input & live discount */}
          <div className="mt-4 border-t border-border pt-4">
            <label htmlFor="promo-code-input" className="block text-[13px] font-semibold text-text-primary mb-1.5">
              {t("checkout.promoLabel")}
            </label>
            {appliedPromo ? (
              <div className="flex items-center justify-between rounded-xl border border-success/30 bg-success-bg px-3 py-2 text-success-text animate-pop-spring">
                <div className="flex items-center gap-2">
                  <Tag size={15} aria-hidden="true" />
                  <span className="font-mono font-bold text-[13px]">{appliedPromo.code}</span>
                  <span className="text-[12px] text-text-secondary">({appliedPromo.label})</span>
                </div>
                <button
                  type="button"
                  onClick={() => {
                    setAppliedPromo(null);
                    setPromoError(null);
                  }}
                  aria-label="Xóa mã giảm giá"
                  className="flex size-6 items-center justify-center rounded-full text-text-secondary transition-colors hover:bg-black/10 hover:text-text-primary active:scale-90"
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : (
              <div className="flex flex-col gap-2">
                <div className="flex gap-2">
                  <div className="relative min-w-0 flex-1">
                    <Ticket
                      size={15}
                      className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 text-text-tertiary"
                      aria-hidden="true"
                    />
                    <input
                      id="promo-code-input"
                      type="text"
                      value={promoInput}
                      onChange={(e) => {
                        setPromoInput(e.target.value.toUpperCase());
                        setPromoError(null);
                      }}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") {
                          e.preventDefault();
                          handleApplyPromo();
                        }
                      }}
                      placeholder={t("checkout.promoPlaceholder")}
                      className="w-full rounded-xl border border-border-strong bg-background-alt py-2 pl-9 pr-3 text-[13px] font-mono font-bold uppercase text-text-primary placeholder:font-sans placeholder:font-normal placeholder:text-text-tertiary focus:border-primary focus:outline-none"
                    />
                  </div>
                  <button
                    type="button"
                    onClick={handleApplyPromo}
                    disabled={!promoInput.trim()}
                    className="rounded-xl bg-chip-bg px-3.5 py-2 text-[12px] font-bold text-primary-dark transition-all hover:bg-info active:scale-95 disabled:opacity-50"
                  >
                    {t("checkout.applyPromo")}
                  </button>
                </div>
                {promoError ? <p className="text-[11px] font-semibold text-danger-text">{promoError}</p> : null}
                <div className="flex flex-wrap items-center gap-1.5 pt-0.5">
                  <span className="text-[11px] text-text-tertiary">{t("checkout.promoHint")}</span>
                  {availablePromos.map((p) => (
                    <button
                      key={p.code}
                      type="button"
                      onClick={() => {
                        setPromoInput(p.code);
                        setAppliedPromo(p);
                        setPromoError(null);
                        toast.success(t("checkout.promoApplied", { code: p.code }));
                      }}
                      className="rounded-md border border-border/70 bg-surface px-2 py-0.5 text-[10px] font-mono font-bold text-primary transition-all hover:border-primary/50 hover:bg-primary/5 active:scale-95"
                    >
                      {p.code}
                    </button>
                  ))}
                </div>
              </div>
            )}
          </div>

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
            {appliedPromo && promoDiscountVnd > 0 ? (
              <div className="flex justify-between gap-4 text-success-text animate-pop-spring">
                <dt className="text-success-text font-medium">
                  {t("checkout.promoDiscount", { code: appliedPromo.code })}
                </dt>
                <dd className="font-bold text-success-text">-{formatVnd(promoDiscountVnd)}</dd>
              </div>
            ) : null}
          </dl>

          <div className="mt-3 flex items-end justify-between gap-4 border-t border-border pt-3">
            <span className="text-[15px] font-bold text-text-primary">{t("checkout.totalLabel")}</span>
            <span className="text-[24px] font-bold leading-none text-primary-dark">{formatVnd(finalTotalVnd)}</span>
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
