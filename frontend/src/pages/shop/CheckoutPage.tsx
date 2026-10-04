import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import { ArrowLeft, Banknote, CreditCard, Info, Lock, MapPin, QrCode } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { MoMoGlyph } from "@/shared/assets/icons/MoMoGlyph";
import { createShopOrder } from "@/features/shop";
import { MOCK_PAYMENT_METHODS, MOCK_VOUCHER, formatVnd, type MockPaymentMethodId } from "./mockData";
import { useCartTotals, useShopCart } from "./useShopCart";

/**
 * `/checkout` — Xác nhận đơn trước khi đặt.
 *
 * KHÔNG có frame Figma riêng cho bước này: thiết kế gộp chọn địa chỉ + phương thức thanh
 * toán vào ngay màn giỏ hàng (`CartPage`) rồi đi thẳng sang "Đặt hàng thành công"
 * (`16:3252`). Quyết định #4 cũng nói rõ KHÔNG spec cổng thanh toán thật. Nên màn này là
 * bước xác nhận cuối: chỉ đọc lại những gì đã chọn ở giỏ hàng, có nhãn cảnh báo là bản
 * dựng giao diện, bấm đặt hàng thì sang màn theo dõi đơn mock.
 */

const PAYMENT_ICONS: Record<MockPaymentMethodId, LucideIcon | null> = {
  momo: null,
  vietqr: QrCode,
  cod: Banknote,
  card: CreditCard,
};

export function CheckoutPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const lines = useShopCart((s) => s.lines);
  const voucherApplied = useShopCart((s) => s.voucherApplied);
  const paymentMethodId = useShopCart((s) => s.paymentMethodId);
  const totals = useCartTotals();
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState(false);
  const [receiverName, setReceiverName] = useState("");
  const [receiverPhone, setReceiverPhone] = useState("");
  const [shippingAddress, setShippingAddress] = useState("");

  const method = MOCK_PAYMENT_METHODS.find((m) => m.id === paymentMethodId) ?? MOCK_PAYMENT_METHODS[0];
  const MethodIcon = PAYMENT_ICONS[method.id];

  return (
    <div className="flex flex-col gap-4 lg:gap-5">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-[20px] font-bold text-text-primary lg:text-[26px]">{t("checkout.title")}</h1>
        <Link
          to="/cart"
          className="flex items-center gap-1.5 text-[12px] font-semibold text-primary-dark hover:underline"
        >
          <ArrowLeft size={14} aria-hidden="true" />
          {t("checkout.back")}
        </Link>
      </div>

      <p className="flex items-start gap-2 rounded-xl bg-info px-3.5 py-2.5 text-[12px] leading-relaxed text-info-text">
        <Info size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
        {t("checkout.mockNotice")}
      </p>

      <div className="flex flex-col gap-4 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <div className="flex items-center justify-between gap-3">
              <h2 className="flex items-center gap-2 text-[14px] font-bold text-text-primary">
                <MapPin size={16} className="text-primary-dark" aria-hidden="true" />
                {t("checkout.addressLabel")}
              </h2>
              <Link to="/cart" className="text-[12px] font-semibold text-primary hover:underline">
                {t("checkout.edit")}
              </Link>
            </div>
            <div className="grid gap-3 pt-3 sm:grid-cols-2">
              <label className="flex flex-col gap-1 text-small font-semibold text-text-secondary">
                {t("checkout.receiverName")}
                <input
                  className="h-10 rounded-lg bg-background-alt px-3 font-normal text-text-primary"
                  value={receiverName}
                  onChange={(event) => {
                    setReceiverName(event.target.value);
                  }}
                  required
                />
              </label>
              <label className="flex flex-col gap-1 text-small font-semibold text-text-secondary">
                {t("checkout.receiverPhone")}
                <input
                  className="h-10 rounded-lg bg-background-alt px-3 font-normal text-text-primary"
                  value={receiverPhone}
                  onChange={(event) => {
                    setReceiverPhone(event.target.value);
                  }}
                  required
                  inputMode="tel"
                />
              </label>
              <label className="flex flex-col gap-1 text-small font-semibold text-text-secondary sm:col-span-2">
                {t("checkout.shippingAddress")}
                <textarea
                  className="min-h-20 rounded-lg bg-background-alt px-3 py-2 font-normal text-text-primary"
                  value={shippingAddress}
                  onChange={(event) => {
                    setShippingAddress(event.target.value);
                  }}
                  required
                />
              </label>
            </div>
          </section>

          <section className="rounded-2xl bg-surface p-4 shadow-brand-md">
            <div className="flex items-center justify-between gap-3">
              <h2 className="text-[14px] font-bold text-text-primary">{t("checkout.paymentMethodLabel")}</h2>
              <Link to="/cart" className="text-[12px] font-semibold text-primary hover:underline">
                {t("checkout.edit")}
              </Link>
            </div>
            <div className="flex items-center gap-3 pt-2.5">
              <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-background-alt text-primary-dark">
                {MethodIcon ? <MethodIcon size={18} aria-hidden="true" /> : <MoMoGlyph size={28} />}
              </span>
              <span className="min-w-0">
                <span className="block text-[14px] font-bold text-text-primary">{method.name}</span>
                <span className="block truncate text-[12px] text-text-secondary">{method.note}</span>
              </span>
            </div>
          </section>
        </div>

        <section className="w-full rounded-2xl bg-surface p-5 shadow-brand-md lg:sticky lg:top-4 lg:w-[380px] lg:shrink-0">
          <h2 className="text-[15px] font-bold text-text-primary">{t("checkout.summaryTitle")}</h2>
          <ul className="flex flex-col gap-3 pt-3">
            {lines.map((line) => (
              <li key={line.id} className="flex items-center gap-3">
                <img src={line.imageUrl} alt="" className="size-11 shrink-0 rounded-lg object-cover" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[12px] font-semibold text-text-primary">{line.name}</p>
                  <p className="text-[11px] text-text-secondary">{t("checkout.qty", { count: line.quantity })}</p>
                </div>
                <span className="shrink-0 text-[13px] font-bold text-primary-dark">
                  {formatVnd(line.unitPrice * line.quantity)}
                </span>
              </li>
            ))}
          </ul>

          <dl className="mt-3 flex flex-col gap-2 border-t border-border pt-3 text-[12px]">
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("cartPanel.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(totals.subtotal)}</dd>
            </div>
            {voucherApplied ? (
              <div className="flex justify-between gap-4">
                <dt className="text-text-secondary">{t("cartPanel.discountLabel", { code: MOCK_VOUCHER.code })}</dt>
                <dd className="font-semibold text-danger">-{formatVnd(totals.discount)}</dd>
              </div>
            ) : null}
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("cartPanel.shippingLabel")}</dt>
              <dd className="font-semibold text-success-text">{t("checkout.serverShipping")}</dd>
            </div>
          </dl>

          <div className="mt-3 flex items-end justify-between gap-4 border-t border-border pt-3">
            <span>
              <span className="block text-[14px] font-bold text-text-primary">{t("cartPanel.totalLabel")}</span>
              <span className="block pt-0.5 text-[11px] text-text-tertiary">{t("cartPage.vatNote")}</span>
            </span>
            <span className="text-[22px] font-bold leading-none text-primary-dark">{formatVnd(totals.total)}</span>
          </div>

          <button
            type="button"
            disabled={
              lines.length === 0 || placing || !receiverName.trim() || !receiverPhone.trim() || !shippingAddress.trim()
            }
            onClick={() => {
              setPlacing(true);
              setError(false);
              void createShopOrder({
                paymentMethod:
                  paymentMethodId === "cod" ? "COD" : paymentMethodId === "momo" ? "MOMO" : "BANK_TRANSFER",
                receiverName: receiverName.trim(),
                receiverPhone: receiverPhone.trim(),
                shippingAddress: shippingAddress.trim(),
              })
                .then((order) => navigate(`/orders/${order.id}`))
                .catch(() => {
                  setError(true);
                })
                .finally(() => {
                  setPlacing(false);
                });
            }}
            className="mt-4 flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
          >
            <Lock size={15} aria-hidden="true" />
            {placing ? t("checkout.placing") : t("checkout.placeOrder")}
          </button>
          {error ? <p className="pt-2 text-center text-[12px] text-danger-text">{t("checkout.error")}</p> : null}
        </section>
      </div>
    </div>
  );
}
