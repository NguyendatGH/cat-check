import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { AlertTriangle, ArrowLeft, ArrowRight, Banknote, Landmark, MapPin, ShoppingCart, X } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { MoMoGlyph } from "@/shared/assets/icons/MoMoGlyph";
import {
  Dialog,
  DialogClose,
  DialogContent,
  DialogDescription,
  DialogTitle,
  EmptyState,
  ErrorState,
  SkeletonLoader,
  toast,
} from "@/shared/ui";
import { useClearCart, useShopCart } from "@/features/shop";
import { usePaymentMethodStore } from "./paymentMethodStore";
import {
  SHOP_PAYMENT_METHOD_IDS,
  cartSavings,
  exceedsStock,
  formatVnd,
  maxOrderQuantity,
  type ShopPaymentMethodId,
} from "./shopFormat";
import { ProductGlyph, QuantityStepper, ShippingFee } from "./shopUi";
import { useCartLineActions } from "./useCartActions";

/**
 * `/cart` — Giỏ hàng (design mobile `Giỏ hàng (Shopping Cart)`; một cột ở mobile, hai cột từ `lg`).
 *
 * Dòng hàng, số lượng, tạm tính, phí vận chuyển, tổng đều là phản hồi `GET/PUT/DELETE /cart`.
 * Số lượng tối đa mỗi dòng = tồn kho thật (`product.stockQuantity`, trần 99 của API).
 *
 * Đã gỡ khỏi mockup (không có API / tuyên bố chưa kiểm chứng): địa chỉ với tên và số điện thoại
 * bịa, khối "đồng bộ hồ sơ mèo cưng/hiệu chuẩn dải màu", mã giảm giá và voucher, quà tặng 0đ,
 * "giao hỏa tốc 2H", ba nhãn "100% kiểm định / đổi trả 30 ngày / bác sĩ 24-7", VietQR và thẻ
 * quốc tế (backend chỉ nhận `COD`/`MOMO`/`BANK_TRANSFER`).
 *
 * Padding ngang do `TaskLayout` cấp.
 */

const PAYMENT_ICONS: Record<ShopPaymentMethodId, LucideIcon | null> = {
  COD: Banknote,
  MOMO: null,
  BANK_TRANSFER: Landmark,
};

function ClearCartButton() {
  const { t } = useTranslation("shop");
  const [open, setOpen] = useState(false);
  const clearCart = useClearCart();
  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <button
        type="button"
        onClick={() => {
          setOpen(true);
        }}
        className="rounded-lg px-2 py-1.5 text-[12px] font-medium text-text-secondary hover:bg-background-alt hover:text-danger"
      >
        {t("cartPage.clearAll")}
      </button>
      <DialogContent>
        <DialogTitle className="pr-8">{t("cartPage.clearConfirmTitle")}</DialogTitle>
        <DialogDescription className="pt-2">{t("cartPage.clearConfirmBody")}</DialogDescription>
        <div className="flex justify-end gap-2 pt-5">
          <DialogClose className="rounded-xl px-4 py-2.5 text-[13px] font-semibold text-text-secondary hover:bg-background-alt">
            {t("cartPage.clearCancel")}
          </DialogClose>
          <button
            type="button"
            disabled={clearCart.isPending}
            onClick={() => {
              clearCart.mutate(undefined, {
                onSuccess: () => {
                  setOpen(false);
                },
                onError: () => {
                  toast.error(t("toast.updateError"));
                },
              });
            }}
            className="rounded-xl bg-danger px-4 py-2.5 text-[13px] font-bold text-white hover:opacity-90 disabled:opacity-50"
          >
            {t("cartPage.clearConfirm")}
          </button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

export function CartPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const cartQuery = useShopCart();
  const { pendingProductId, isUpdating, setQuantity, remove } = useCartLineActions();
  const paymentMethodId = usePaymentMethodStore((s) => s.paymentMethodId);
  const setPaymentMethod = usePaymentMethodStore((s) => s.setPaymentMethod);

  const cart = cartQuery.data;
  const lines = cart?.lines ?? [];
  const isEmpty = lines.length === 0;
  const hasStockIssue = lines.some(exceedsStock);
  const savings = cartSavings(cart);
  const canCheckout = !isEmpty && !hasStockIssue && !isUpdating;

  const goCheckout = () => {
    void navigate("/checkout");
  };

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="flex items-center gap-2.5 text-[22px] font-bold text-text-primary lg:text-[26px]">
          {t("cartPage.title")}
          {cart ? (
            <span className="rounded-full bg-chip-bg px-2.5 py-0.5 text-[12px] font-semibold text-primary-dark">
              {t("cartPage.count", { count: lines.length })}
            </span>
          ) : null}
        </h1>
        <div className="flex items-center gap-3">
          <Link
            to="/shop"
            className="hidden items-center gap-1.5 text-[12px] font-semibold text-primary-dark hover:underline lg:flex"
          >
            <ArrowLeft size={14} aria-hidden="true" />
            {t("cartPage.continueShopping")}
          </Link>
          {!isEmpty ? <ClearCartButton /> : null}
        </div>
      </div>

      {cartQuery.isPending ? (
        <div className="flex flex-col gap-4 lg:flex-row lg:gap-6" aria-hidden="true">
          <div className="flex flex-1 flex-col gap-4">
            <SkeletonLoader className="h-20 rounded-2xl" />
            <SkeletonLoader className="h-[220px] rounded-2xl" />
          </div>
          <SkeletonLoader className="h-[360px] rounded-2xl lg:w-[380px]" />
        </div>
      ) : cartQuery.isError ? (
        <ErrorState
          title={t("cartPage.error")}
          onRetry={() => {
            void cartQuery.refetch();
          }}
          className="rounded-2xl bg-surface shadow-brand-md"
        />
      ) : isEmpty ? (
        <EmptyState
          icon={<ShoppingCart size={22} />}
          title={t("cartPage.empty")}
          description={t("cartPage.emptyBody")}
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
      ) : (
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
          {/* ---------- Cột trái: nội dung đơn ---------- */}
          <div className="flex min-w-0 flex-1 flex-col gap-4">
            {/* Địa chỉ nhận hàng chỉ được nhập ở `/checkout` — không có sổ địa chỉ trong API. */}
            <section className="flex gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-chip-bg text-primary-dark">
                <MapPin size={18} aria-hidden="true" />
              </span>
              <div className="min-w-0">
                <p className="text-[14px] font-bold text-text-primary">{t("cartPage.addressTitle")}</p>
                <p className="pt-1 text-[13px] leading-relaxed text-text-secondary">{t("cartPage.addressBody")}</p>
              </div>
            </section>

            <ul className="divide-y divide-border overflow-hidden rounded-2xl bg-surface shadow-brand-md">
              {lines.map((line) => {
                const { product } = line;
                const pending = pendingProductId === product.id;
                const compare = product.compareAtPriceVnd;
                return (
                  <li key={product.id} className="relative flex gap-3.5 p-4">
                    <Link to={`/shop/products/${product.id}`} className="shrink-0" tabIndex={-1} aria-hidden="true">
                      <ProductGlyph
                        sku={product.sku}
                        imageUrl={product.imageUrl}
                        className="size-[72px]"
                        iconSize={28}
                      />
                    </Link>
                    <div className="flex min-w-0 flex-1 flex-col gap-1">
                      <Link
                        to={`/shop/products/${product.id}`}
                        className="pr-8 text-[14px] font-bold leading-snug text-text-primary hover:text-primary-dark"
                      >
                        {product.name}
                      </Link>
                      <p className="text-[12px] text-text-secondary">
                        {t("cartPage.unitPrice", { sku: product.sku, price: formatVnd(product.priceVnd) })}
                      </p>
                      {exceedsStock(line) ? (
                        <p className="flex items-start gap-1 text-[12px] font-semibold leading-snug text-danger-text">
                          <AlertTriangle size={12} className="mt-0.5 shrink-0" aria-hidden="true" />
                          {product.stockQuantity <= 0
                            ? t("cartPage.outOfStockLine")
                            : t("cartPage.onlyLeft", { count: product.stockQuantity })}
                        </p>
                      ) : null}
                      <div className="flex items-end justify-between gap-2 pt-1 md:items-center">
                        <span className="flex min-w-0 flex-col md:flex-row md:items-baseline md:gap-1.5">
                          <span className="text-[16px] font-bold text-primary-dark">{formatVnd(line.totalVnd)}</span>
                          {compare && compare > product.priceVnd ? (
                            <s className="text-[12px] text-text-tertiary">{formatVnd(compare * line.quantity)}</s>
                          ) : null}
                        </span>
                        <QuantityStepper
                          value={line.quantity}
                          max={maxOrderQuantity(product.stockQuantity)}
                          pending={pending}
                          onChange={(next) => {
                            setQuantity(product.id, next);
                          }}
                        />
                      </div>
                    </div>
                    <button
                      type="button"
                      aria-label={t("cartPanel.removeItem", { name: product.name })}
                      disabled={pending}
                      onClick={() => {
                        remove(product.id);
                      }}
                      className="absolute right-2 top-2 flex size-9 items-center justify-center rounded-lg text-text-tertiary hover:bg-background-alt hover:text-danger disabled:opacity-40"
                    >
                      <X size={16} aria-hidden="true" />
                    </button>
                  </li>
                );
              })}
            </ul>

            <fieldset className="rounded-2xl bg-surface p-4 shadow-brand-md">
              <legend className="contents">
                <span className="text-[15px] font-bold text-text-primary">{t("cartPage.paymentTitle")}</span>
              </legend>
              <div className="flex flex-col gap-2.5 pt-3">
                {SHOP_PAYMENT_METHOD_IDS.map((methodId) => {
                  const Icon = PAYMENT_ICONS[methodId];
                  const selected = paymentMethodId === methodId;
                  return (
                    <label
                      key={methodId}
                      className={cn(
                        "flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary",
                        selected
                          ? "border-primary-dark bg-chip-bg/40"
                          : "border-border bg-surface hover:border-border-strong",
                      )}
                    >
                      <input
                        type="radio"
                        name="payment-method"
                        value={methodId}
                        checked={selected}
                        onChange={() => {
                          setPaymentMethod(methodId);
                        }}
                        className="sr-only"
                      />
                      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background-alt text-primary-dark">
                        {Icon ? <Icon size={18} aria-hidden="true" /> : <MoMoGlyph size={28} />}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block text-[14px] font-bold leading-snug text-text-primary">
                          {t(`payment.methods.${methodId}.name`)}
                        </span>
                        <span className="block pt-0.5 text-[12px] leading-relaxed text-text-secondary">
                          {t(`payment.methods.${methodId}.note`)}
                        </span>
                      </span>
                      <span
                        aria-hidden="true"
                        className={cn(
                          "flex size-5 shrink-0 items-center justify-center rounded-full border-2",
                          selected ? "border-primary-dark" : "border-border-strong",
                        )}
                      >
                        {selected ? <span className="size-2.5 rounded-full bg-primary-dark" /> : null}
                      </span>
                    </label>
                  );
                })}
              </div>
            </fieldset>
          </div>

          {/* ---------- Cột phải: tóm tắt thanh toán (dính ở lg) ---------- */}
          <div className="flex w-full flex-col gap-4 lg:sticky lg:top-20 lg:w-[360px] lg:shrink-0">
            <section className="rounded-2xl bg-surface p-4 shadow-brand-md" aria-busy={isUpdating || undefined}>
              <h2 className="text-[15px] font-bold text-text-primary">{t("cartPage.summaryTitle")}</h2>
              <div className={cn("transition-opacity", isUpdating && "opacity-60")}>
                <dl className="flex flex-col gap-2.5 pt-3 text-[13px]">
                  <div className="flex justify-between gap-4">
                    <dt className="text-text-secondary">{t("cartPage.subtotalLine", { count: lines.length })}</dt>
                    <dd className="font-semibold text-text-primary">{formatVnd(cart?.subtotalVnd ?? 0)}</dd>
                  </div>
                  <div className="flex justify-between gap-4">
                    <dt className="text-text-secondary">{t("cartPage.shippingLine")}</dt>
                    <dd className="text-right font-semibold text-text-primary">
                      <ShippingFee value={cart?.shippingFeeVnd ?? 0} />
                    </dd>
                  </div>
                </dl>
                <div className="mt-3 flex items-end justify-between gap-4 border-t border-border pt-3">
                  <span>
                    <span className="block text-[15px] font-bold text-text-primary">{t("cartPage.totalLabel")}</span>
                    {savings > 0 ? (
                      <span className="block pt-0.5 text-[12px] font-semibold text-success-text">
                        {t("cartPage.savingsLine", { amount: formatVnd(savings) })}
                      </span>
                    ) : null}
                  </span>
                  <span className="text-[24px] font-bold leading-none text-primary-dark">
                    {formatVnd(cart?.totalVnd ?? 0)}
                  </span>
                </div>
              </div>
              {hasStockIssue ? (
                <p
                  role="alert"
                  className="mt-3 rounded-lg bg-danger-bg px-3 py-2 text-[12px] leading-relaxed text-danger-text"
                >
                  {t("cartPage.stockIssue")}
                </p>
              ) : null}
              <button
                type="button"
                disabled={!canCheckout}
                onClick={goCheckout}
                className="mt-4 hidden w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50 lg:flex"
              >
                {t("cartPage.proceed")}
                <ArrowRight size={16} aria-hidden="true" />
              </button>
            </section>
          </div>
        </div>
      )}

      {/* Thanh CTA dính đáy — chỉ mobile (TaskLayout không có bottom nav); ở lg nút nằm trong
          khối tóm tắt bên phải. */}
      {!isEmpty ? (
        <div className="sticky bottom-0 z-10 -mx-4 -mb-6 flex items-center gap-3 border-t border-border bg-surface px-4 py-3 shadow-brand-lg lg:hidden">
          <span className="min-w-0 flex-1">
            <span className="block text-[12px] text-text-secondary">{t("cartPage.totalLabel")}</span>
            <span className="block text-[20px] font-bold leading-tight text-primary-dark">
              {formatVnd(cart?.totalVnd ?? 0)}
            </span>
          </span>
          <button
            type="button"
            disabled={!canCheckout}
            onClick={goCheckout}
            className="flex shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-5 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
          >
            {t("cartPage.proceed")}
            <ArrowRight size={16} aria-hidden="true" />
          </button>
        </div>
      ) : null}
    </div>
  );
}
