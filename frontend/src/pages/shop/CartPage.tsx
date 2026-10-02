import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  ArrowLeft,
  ArrowRight,
  BadgeCheck,
  Banknote,
  Check,
  CreditCard,
  Gift,
  Lock,
  MapPin,
  Minus,
  PawPrint,
  Plus,
  QrCode,
  RefreshCw,
  RotateCcw,
  Stethoscope,
  Ticket,
  X,
  Zap,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { MoMoGlyph } from "@/shared/assets/icons/MoMoGlyph";
import {
  MOCK_CART_CAT_SYNC,
  MOCK_CART_TRUST,
  MOCK_DELIVERY_ADDRESS,
  MOCK_PAYMENT_METHODS,
  MOCK_SHIPPING_LABEL,
  MOCK_VOUCHER,
  MOCK_VOUCHER_AVAILABLE_COUNT,
  formatVnd,
  type MockPaymentMethodId,
} from "./mockData";
import { useCartTotals, useShopCart } from "./useShopCart";

/**
 * `/cart` — Giỏ hàng dạng màn riêng.
 *
 * Nguồn thiết kế là bản MOBILE `Giỏ hàng (Shopping Cart)` (450×1949): nó là một màn gộp
 * giỏ hàng + thanh toán (địa chỉ giao → đồng bộ hồ sơ mèo → danh sách món → voucher →
 * phương thức thanh toán → tóm tắt → thanh CTA dính đáy). Bản WEB `16:5849` KHÔNG có màn
 * này — ở web giỏ hàng là cột phải của trang Cửa hàng (`CartPanel`), nên từ `lg` trang này
 * trải thành 2 cột: nội dung đơn bên trái, khối thanh toán dính bên phải.
 *
 * Toàn bộ dữ liệu là mock (`mockData.ts`) — module Shop chưa có API backend nào.
 * Padding ngang ở `lg` do `AppLayout` cấp, nên ở đây là `lg:px-0`.
 */

const PAYMENT_ICONS: Record<MockPaymentMethodId, LucideIcon | null> = {
  momo: null,
  vietqr: QrCode,
  cod: Banknote,
  card: CreditCard,
};

const TRUST_ICONS: Record<(typeof MOCK_CART_TRUST)[number]["key"], LucideIcon> = {
  verified: BadgeCheck,
  refund: RotateCcw,
  vet: Stethoscope,
};

const TRUST_TONES: Record<(typeof MOCK_CART_TRUST)[number]["key"], string> = {
  verified: "bg-info text-primary-dark",
  refund: "bg-warning-bg text-warning-text",
  vet: "bg-success-bg text-success-text",
};

export function CartPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const lines = useShopCart((s) => s.lines);
  const voucherApplied = useShopCart((s) => s.voucherApplied);
  const paymentMethodId = useShopCart((s) => s.paymentMethodId);
  const increase = useShopCart((s) => s.increase);
  const decrease = useShopCart((s) => s.decrease);
  const remove = useShopCart((s) => s.remove);
  const removeVoucher = useShopCart((s) => s.removeVoucher);
  const setPaymentMethod = useShopCart((s) => s.setPaymentMethod);
  const clearAll = useShopCart((s) => s.clearAll);
  const totals = useCartTotals();
  const isEmpty = lines.length === 0;

  const goCheckout = () => {
    void navigate("/checkout");
  };

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="flex items-center gap-2.5 text-[20px] font-bold text-text-primary lg:text-[26px]">
          {t("cartPage.title")}
          <span className="rounded-full bg-chip-bg px-2.5 py-0.5 text-[12px] font-semibold text-primary-dark">
            {t("cartPage.count", { count: totals.itemCount })}
          </span>
        </h1>
        <div className="flex items-center gap-4">
          <Link
            to="/shop"
            className="hidden items-center gap-1.5 text-[12px] font-semibold text-primary-dark hover:underline lg:flex"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            {t("cartPage.continueShopping")}
          </Link>
          <button
            type="button"
            onClick={clearAll}
            disabled={isEmpty}
            className="text-[12px] font-medium text-text-secondary hover:text-danger disabled:cursor-not-allowed disabled:opacity-40"
          >
            {t("cartPage.clearAll")}
          </button>
        </div>
      </div>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        {/* ---------- Cột trái: nội dung đơn ---------- */}
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <div className="flex items-center gap-3">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-chip-bg text-primary-dark">
                <MapPin size={18} aria-hidden="true" />
              </span>
              <p className="min-w-0 flex-1 truncate text-[15px] font-bold text-text-primary">
                {MOCK_DELIVERY_ADDRESS.receiverName}
                <span className="pl-2 text-[13px] font-normal text-text-tertiary">
                  {MOCK_DELIVERY_ADDRESS.phoneMasked}
                </span>
              </p>
              <button type="button" className="shrink-0 text-[13px] font-semibold text-primary hover:underline">
                {t("cartPage.addressChange")}
              </button>
            </div>
            <p className="pl-13 pt-1.5 text-[13px] leading-relaxed text-text-secondary">
              {MOCK_DELIVERY_ADDRESS.line}
            </p>
            <p className="ml-13 mt-3 inline-flex items-center gap-1.5 rounded-full bg-warning-bg px-3 py-1.5 text-[12px] font-semibold text-warning-text">
              <Zap size={13} aria-hidden="true" />
              {MOCK_DELIVERY_ADDRESS.speedBadge}
            </p>
          </section>

          <section className="flex gap-3 rounded-2xl bg-info/60 p-4">
            <span className="relative flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-primary-dark">
              <PawPrint size={18} aria-hidden="true" />
              <span className="absolute -right-1 -top-1 size-3 rounded-full border-2 border-info bg-success" />
            </span>
            <div className="min-w-0">
              <p className="flex flex-wrap items-center gap-2 text-[14px] font-bold text-text-primary">
                {t("cartPage.catSyncTitle")}
                <span className="rounded-md bg-primary-dark px-2 py-0.5 text-[11px] font-semibold text-white">
                  {MOCK_CART_CAT_SYNC.catName}
                </span>
              </p>
              <p className="pt-1 text-[13px] leading-relaxed text-text-secondary">
                {t("cartPage.catSyncBody", MOCK_CART_CAT_SYNC)}
              </p>
            </div>
          </section>

          {isEmpty ? (
            <section className="flex flex-col items-center gap-3 rounded-2xl bg-surface p-8 text-center shadow-brand-md">
              <p className="text-[14px] text-text-secondary">{t("cartPage.empty")}</p>
              <Link
                to="/shop"
                className="rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
              >
                {t("cartPage.emptyCta")}
              </Link>
            </section>
          ) : (
            <ul className="divide-y divide-border overflow-hidden rounded-2xl bg-surface shadow-brand-md">
              {lines.map((line) => (
                <li key={line.id} className="relative flex gap-3.5 p-4">
                  <span className="relative size-[72px] shrink-0 overflow-hidden rounded-xl">
                    <img src={line.imageUrl} alt="" className="size-full object-cover" />
                    {line.isGift ? (
                      <span className="absolute left-0 top-0 rounded-br-lg bg-secondary px-1.5 py-0.5 text-[10px] font-bold text-secondary-text-on">
                        {t("cartPage.giftRibbon")}
                      </span>
                    ) : null}
                  </span>
                  <div className="flex min-w-0 flex-1 flex-col gap-1.5">
                    <p className="pr-6 text-[14px] font-bold leading-snug text-text-primary">{line.name}</p>
                    <p className="text-[12px] text-text-secondary">{line.subtitle}</p>
                    <p
                      className={cn(
                        "inline-flex w-fit items-center gap-1.5 rounded-lg px-2.5 py-1 text-[12px] font-semibold",
                        line.isGift
                          ? "bg-success-bg text-success-text"
                          : "bg-chip-bg text-primary-dark",
                      )}
                    >
                      {line.isGift ? (
                        <Gift size={13} aria-hidden="true" />
                      ) : (
                        <RefreshCw size={13} aria-hidden="true" />
                      )}
                      {line.isGift ? t("cartPage.giftBadge") : MOCK_VOUCHER.note}
                    </p>
                    <div className="flex flex-wrap items-center justify-between gap-2 pt-1">
                      <span className="flex items-baseline gap-1.5">
                        <span
                          className={cn(
                            "text-[16px] font-bold",
                            line.isGift ? "text-success-text" : "text-primary-dark",
                          )}
                        >
                          {formatVnd(line.unitPrice * line.quantity)}
                        </span>
                        {line.compareAtPrice ? (
                          <s className="text-[12px] text-text-tertiary">{formatVnd(line.compareAtPrice)}</s>
                        ) : null}
                      </span>
                      {line.isGift ? (
                        <span className="rounded-lg border border-border px-3 py-1.5 text-[12px] font-medium text-text-secondary">
                          {t("cartPage.giftQty", { count: line.quantity })}
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 rounded-lg border border-border px-1 py-1">
                          <button
                            type="button"
                            aria-label={t("cartPanel.decrease")}
                            onClick={() => { decrease(line.id); }}
                            className="flex size-7 items-center justify-center rounded-md text-text-secondary hover:bg-background-alt"
                          >
                            <Minus size={14} aria-hidden="true" />
                          </button>
                          <span className="min-w-6 text-center text-[13px] font-bold text-text-primary">
                            {line.quantity}
                          </span>
                          <button
                            type="button"
                            aria-label={t("cartPanel.increase")}
                            onClick={() => { increase(line.id); }}
                            className="flex size-7 items-center justify-center rounded-md text-text-secondary hover:bg-background-alt"
                          >
                            <Plus size={14} aria-hidden="true" />
                          </button>
                        </span>
                      )}
                    </div>
                  </div>
                  {!line.isGift ? (
                    <button
                      type="button"
                      aria-label={t("cartPanel.removeItem")}
                      onClick={() => { remove(line.id); }}
                      className="absolute right-3 top-3 flex size-6 items-center justify-center rounded-md text-text-tertiary hover:bg-background-alt hover:text-danger"
                    >
                      <X size={15} aria-hidden="true" />
                    </button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <div className="flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-[14px] font-bold text-text-primary">
                <Ticket size={16} className="text-secondary-text-on" aria-hidden="true" />
                {t("cartPage.voucherSectionTitle")}
              </h2>
              <button type="button" className="text-[12px] font-semibold text-primary hover:underline">
                {t("cartPage.voucherSeeAll", { count: MOCK_VOUCHER_AVAILABLE_COUNT })}
              </button>
            </div>
            {voucherApplied ? (
              <div className="mt-3 flex items-center gap-3 rounded-xl border border-success bg-success-bg/50 p-2.5">
                <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-success text-white">
                  <Check size={17} strokeWidth={3} aria-hidden="true" />
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[13px] font-bold text-text-primary">
                    {MOCK_VOUCHER.code}
                    <span className="pl-2 text-[12px] font-semibold text-success-text">
                      -{formatVnd(MOCK_VOUCHER.discountAmount)}
                    </span>
                  </p>
                  <p className="truncate text-[12px] text-text-secondary">
                    {t("cartPage.voucherAppliedNote", { amount: formatVnd(MOCK_VOUCHER.discountAmount) })}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={removeVoucher}
                  className="shrink-0 rounded-lg border border-border bg-surface px-3 py-1.5 text-[12px] font-semibold text-text-primary hover:border-border-strong"
                >
                  {t("cartPage.voucherSwap")}
                </button>
              </div>
            ) : (
              <p className="pt-3 text-[12px] text-text-tertiary">{t("cartPage.voucherNone")}</p>
            )}
          </section>
        </div>

        {/* ---------- Cột phải: thanh toán (dính ở lg) ---------- */}
        <div className="flex w-full flex-col gap-4 lg:sticky lg:top-4 lg:w-[380px] lg:shrink-0">
          <fieldset className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <div className="flex items-center justify-between gap-3">
              <legend className="contents">
                <span className="text-[15px] font-bold text-text-primary">{t("cartPage.paymentTitle")}</span>
              </legend>
              <span className="inline-flex items-center gap-1.5 rounded-full bg-success-bg px-2.5 py-1 text-[12px] font-semibold text-success-text">
                <Lock size={12} aria-hidden="true" />
                {t("cartPage.paymentSsl")}
              </span>
            </div>
            <div className="flex flex-col gap-2.5 pt-3">
              {MOCK_PAYMENT_METHODS.map((method) => {
                const Icon = PAYMENT_ICONS[method.id];
                const selected = paymentMethodId === method.id;
                return (
                  <label
                    key={method.id}
                    className={cn(
                      "flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors",
                      selected
                        ? "border-primary bg-chip-bg/40"
                        : "border-border bg-background-alt/50 hover:border-border-strong",
                    )}
                  >
                    <input
                      type="radio"
                      name="payment-method"
                      value={method.id}
                      checked={selected}
                      onChange={() => { setPaymentMethod(method.id); }}
                      className="sr-only"
                    />
                    <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-primary-dark">
                      {Icon ? <Icon size={18} aria-hidden="true" /> : <MoMoGlyph size={28} />}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex flex-wrap items-center gap-2">
                        <span className="text-[14px] font-bold text-text-primary">{method.name}</span>
                        {method.recommended ? (
                          <span className="rounded-md bg-warning-bg px-2 py-0.5 text-[11px] font-semibold text-warning-text">
                            {t("cartPage.paymentRecommended")}
                          </span>
                        ) : null}
                      </span>
                      <span className="block truncate pt-0.5 text-[12px] text-text-secondary">{method.note}</span>
                    </span>
                    <span
                      aria-hidden="true"
                      className={cn(
                        "flex size-5 shrink-0 items-center justify-center rounded-full border-2",
                        selected ? "border-primary" : "border-border-strong",
                      )}
                    >
                      {selected ? <span className="size-2.5 rounded-full bg-primary" /> : null}
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <h2 className="text-[15px] font-bold text-text-primary">{t("cartPage.summaryTitle")}</h2>
            <dl className="flex flex-col gap-2.5 pt-3 text-[13px]">
              <div className="flex justify-between gap-4">
                <dt className="text-text-secondary">{t("cartPage.subtotalLine", { count: totals.itemCount })}</dt>
                <dd className="font-semibold text-text-primary">{formatVnd(totals.subtotal)}</dd>
              </div>
              {voucherApplied ? (
                <div className="flex justify-between gap-4">
                  <dt className="text-text-secondary">
                    {t("cartPanel.discountLabel", { code: MOCK_VOUCHER.code })}
                  </dt>
                  <dd className="font-semibold text-success-text">-{formatVnd(totals.discount)}</dd>
                </div>
              ) : null}
              <div className="flex justify-between gap-4">
                <dt className="text-text-secondary">{t("cartPage.shippingLine")}</dt>
                <dd className="font-semibold text-success-text">{MOCK_SHIPPING_LABEL}</dd>
              </div>
            </dl>
            <div className="mt-3 flex items-end justify-between gap-4 border-t border-border pt-3">
              <span>
                <span className="block text-[15px] font-bold text-text-primary">{t("cartPage.totalLabel")}</span>
                {totals.savings > 0 ? (
                  <span className="block pt-0.5 text-[12px] font-semibold text-success-text">
                    {t("cartPage.savingsLabel")} {formatVnd(totals.savings)}
                  </span>
                ) : null}
              </span>
              <span className="text-[24px] font-bold leading-none text-primary-dark">
                {formatVnd(totals.total)}
              </span>
            </div>
            <button
              type="button"
              disabled={isEmpty}
              onClick={goCheckout}
              className="mt-4 hidden w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50 lg:flex"
            >
              {t("cartPage.proceed")}
              <ArrowRight size={16} aria-hidden="true" />
            </button>
          </section>

          <ul className="grid grid-cols-3 gap-2.5">
            {MOCK_CART_TRUST.map((item) => {
              const Icon = TRUST_ICONS[item.key];
              return (
                <li
                  key={item.key}
                  className="flex flex-col items-center gap-1.5 rounded-2xl bg-surface p-3 text-center shadow-brand-md"
                >
                  <span
                    className={cn(
                      "flex size-9 items-center justify-center rounded-full",
                      TRUST_TONES[item.key],
                    )}
                  >
                    <Icon size={17} aria-hidden="true" />
                  </span>
                  <span className="text-[12px] font-bold leading-tight text-text-primary">{item.title}</span>
                  <span className="text-[11px] text-text-tertiary">{item.note}</span>
                </li>
              );
            })}
          </ul>
        </div>
      </div>

      {/* Thanh CTA dính đáy — chỉ mobile; ở lg nút nằm trong khối tóm tắt bên phải. */}
      {!isEmpty ? (
        <div className="sticky bottom-0 -mx-4 -mb-6 flex items-center gap-3 border-t border-border bg-surface px-4 py-3 shadow-brand-lg lg:hidden">
          <span className="min-w-0 flex-1">
            <span className="block text-[12px] text-text-secondary">{t("cartPage.totalLabel")}</span>
            <span className="block text-[20px] font-bold leading-tight text-primary-dark">
              {formatVnd(totals.total)}
            </span>
            <span className="block text-[11px] text-text-tertiary">{t("cartPage.vatNote")}</span>
          </span>
          <button
            type="button"
            onClick={goCheckout}
            className="flex shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-5 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
          >
            {t("cartPage.proceed")}
            <ArrowRight size={16} aria-hidden="true" />
          </button>
        </div>
      ) : null}
    </div>
  );
}
