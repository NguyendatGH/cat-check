import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Banknote, QrCode } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useShopPaymentMethods } from "@/features/shop";
import { usePaymentMethodStore } from "./paymentMethodStore";
import { SHOP_PAYMENT_METHOD_IDS, type ShopPaymentMethodId } from "./shopFormat";

const PAYMENT_ICONS: Record<ShopPaymentMethodId, LucideIcon> = {
  COD: Banknote,
  PAYOS: QrCode,
};

// Dùng ở cả giỏ hàng lẫn checkout: người bấm "Mua ngay" đi thẳng vào checkout vẫn chọn được phương thức.
export function PaymentMethodPicker({ title }: { title: string }) {
  const { t } = useTranslation("shop");
  const paymentMethodId = usePaymentMethodStore((s) => s.paymentMethodId);
  const setPaymentMethod = usePaymentMethodStore((s) => s.setPaymentMethod);
  // COD only until the server confirms PayOS is configured (no keys ⇒ checkout with PAYOS would be a 503).
  const serverData = useShopPaymentMethods().data;
  const serverMethods = serverData ?? ["COD"];
  const paymentMethodIds = SHOP_PAYMENT_METHOD_IDS.filter((id) => serverMethods.includes(id));
  const paymentMethodOffered = paymentMethodIds.includes(paymentMethodId);
  // Chỉ ép về COD khi server đã trả danh sách: lúc đang tải mà reset thì mất lựa chọn đã lưu sau khi tải lại trang.
  const loaded = serverData !== undefined;
  useEffect(() => {
    if (loaded && !paymentMethodOffered) setPaymentMethod("COD");
  }, [loaded, paymentMethodOffered, setPaymentMethod]);

  return (
    <fieldset className="rounded-2xl bg-surface p-4 shadow-brand-md">
      <legend className="contents">
        <span className="text-[15px] font-bold text-text-primary">{title}</span>
      </legend>
      <div className="flex flex-col gap-2.5 pt-3">
        {paymentMethodIds.map((methodId) => {
          const Icon = PAYMENT_ICONS[methodId];
          const selected = paymentMethodId === methodId;
          return (
            <label
              key={methodId}
              className={cn(
                "flex cursor-pointer items-center gap-3 rounded-xl border p-3 transition-colors has-[:focus-visible]:outline has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-primary",
                selected ? "border-primary-dark bg-chip-bg/40" : "border-border bg-surface hover:border-border-strong",
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
                <Icon size={18} aria-hidden="true" />
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
  );
}
