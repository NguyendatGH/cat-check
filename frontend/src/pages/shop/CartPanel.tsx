import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import { Check, Lock, Minus, Plus, RotateCcw, ShoppingBag, X } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatVnd } from "./mockData";
import { useCartTotals, useShopCart } from "./useShopCart";

/**
 * Cột giỏ hàng bên phải trang Cửa hàng (Figma web `16:5849`, khối "Giỏ hàng của bạn").
 * Bản web gộp giỏ hàng vào ngay trang shop thay vì tách màn riêng như bản mobile —
 * `/cart` vẫn dùng lại đúng component này ở dạng một cột.
 */
export function CartPanel({ standalone = false }: { standalone?: boolean }) {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const lines = useShopCart((s) => s.lines);
  const increase = useShopCart((s) => s.increase);
  const decrease = useShopCart((s) => s.decrease);
  const remove = useShopCart((s) => s.remove);
  const totals = useCartTotals();

  return (
    <aside className="flex flex-col gap-4 rounded-2xl bg-surface p-6 shadow-brand-lg">
      <div className="flex items-center justify-between">
        <h2 className="flex items-center gap-2 text-[17px] font-bold text-text-primary">
          <ShoppingBag size={18} className="text-primary-dark" aria-hidden="true" />
          {t("cartPanel.title")}
        </h2>
        <span className="rounded-full bg-chip-bg px-2.5 py-0.5 text-[11px] font-semibold text-primary-dark">
          {t("cartPanel.count", { count: totals.itemCount })}
        </span>
      </div>

      {lines.length === 0 ? (
        <p className="py-6 text-center text-caption text-text-secondary">{t("cartPanel.empty")}</p>
      ) : (
        <>
          <div className="rounded-xl bg-background-alt p-3">
            <p className="flex items-start gap-2 text-[12px] font-semibold text-verified-deep">
              <Check size={14} className="mt-0.5 shrink-0" aria-hidden="true" />
              {totals.shippingFee === 0 ? t("cartPanel.freeShipping") : t("cartPanel.shippingCalculated")}
            </p>
            <div className="mt-2 h-1.5 w-full overflow-hidden rounded-full bg-chip-bg">
              <div className="h-full w-full rounded-full bg-verified-deep" />
            </div>
          </div>

          <ul className="flex flex-col gap-3">
            {lines.map((line) => (
              <li
                key={line.id}
                className={cn("relative flex gap-3 rounded-xl p-2.5", line.isGift ? "bg-background-alt" : "bg-surface")}
              >
                <img src={line.imageUrl} alt="" className="size-16 shrink-0 rounded-lg object-cover" />
                <div className="min-w-0 flex-1">
                  <p className="truncate pr-5 text-[13px] font-semibold text-text-primary">{line.name}</p>
                  <p className="truncate text-[11px] text-text-secondary">{line.subtitle}</p>
                  <div className="mt-1.5 flex items-center justify-between gap-2">
                    {/* Dòng quà tặng: thiết kế chỉ in giá gốc gạch ngang ở bên phải, không có
                        bộ đổi số lượng và cũng không lặp lại "0đ" ở bên trái. */}
                    {line.isGift ? (
                      <span />
                    ) : (
                      <span className="inline-flex items-center gap-1 rounded-lg bg-background-alt px-1 py-0.5">
                        <button
                          type="button"
                          aria-label={t("cartPanel.decrease")}
                          onClick={() => {
                            decrease(line.id);
                          }}
                          className="flex size-5 items-center justify-center rounded text-text-secondary hover:bg-chip-bg"
                        >
                          <Minus size={12} aria-hidden="true" />
                        </button>
                        <span className="min-w-4 text-center text-[12px] font-semibold text-text-primary">
                          {line.quantity}
                        </span>
                        <button
                          type="button"
                          aria-label={t("cartPanel.increase")}
                          onClick={() => {
                            increase(line.id);
                          }}
                          className="flex size-5 items-center justify-center rounded text-text-secondary hover:bg-chip-bg"
                        >
                          <Plus size={12} aria-hidden="true" />
                        </button>
                      </span>
                    )}
                    <span className="flex items-baseline gap-1">
                      {line.compareAtPrice ? (
                        <s className="text-[11px] text-text-tertiary">{formatVnd(line.compareAtPrice)}</s>
                      ) : null}
                      <span className="text-[13px] font-bold text-primary-dark">
                        {formatVnd(line.unitPrice * line.quantity)}
                      </span>
                    </span>
                  </div>
                </div>
                {!line.isGift ? (
                  <button
                    type="button"
                    aria-label={t("cartPanel.removeItem")}
                    onClick={() => {
                      remove(line.id);
                    }}
                    className="absolute right-1.5 top-1.5 flex size-5 items-center justify-center rounded text-text-tertiary hover:bg-background-alt hover:text-danger"
                  >
                    <X size={13} aria-hidden="true" />
                  </button>
                ) : null}
              </li>
            ))}
          </ul>

          <dl className="flex flex-col gap-1.5 border-t border-border pt-3 text-[13px]">
            <div className="flex justify-between">
              <dt className="text-text-secondary">{t("cartPanel.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(totals.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-text-secondary">{t("cartPanel.shippingLabel")}</dt>
              <dd className="font-semibold text-success-text">{formatVnd(totals.shippingFee)}</dd>
            </div>
          </dl>

          <div className="flex items-end justify-between border-t border-border pt-3">
            <span className="text-[15px] font-bold text-text-primary">{t("cartPanel.totalLabel")}</span>
            <span className="text-right">
              <span className="block text-[22px] font-bold leading-7 text-primary-dark">{formatVnd(totals.total)}</span>
              <span className="text-[11px] text-text-tertiary">{t("cartPanel.vatNote")}</span>
            </span>
          </div>

          <button
            type="button"
            onClick={() => {
              void navigate(standalone ? "/checkout" : "/checkout");
            }}
            className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
          >
            <Lock size={15} aria-hidden="true" />
            {t("cartPanel.checkout")}
          </button>

          <div className="flex justify-between text-[11px] text-text-tertiary">
            <span className="flex items-center gap-1">
              <Lock size={11} aria-hidden="true" />
              {t("cartPanel.trustSsl")}
            </span>
            <span className="flex items-center gap-1">
              <RotateCcw size={11} aria-hidden="true" />
              {t("cartPanel.trustRefund")}
            </span>
          </div>
        </>
      )}
    </aside>
  );
}
