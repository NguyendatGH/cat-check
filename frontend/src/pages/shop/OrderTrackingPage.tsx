import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { useQuery } from "@tanstack/react-query";
import { Check, Circle, Loader2, Package, Truck, X } from "lucide-react";
import { getShopOrder } from "@/features/shop";
import { formatVnd } from "./mockData";

const ORDER_STAGES = ["PENDING_PAYMENT", "PAID", "PACKING", "SHIPPING", "DELIVERED"] as const;
const KNOWN_STATUSES = [...ORDER_STAGES, "CANCELLED"] as const;

/** `/orders/:orderId` — đơn hàng và trạng thái giao hàng đọc trực tiếp từ Shop API. */
export function OrderTrackingPage() {
  const { t } = useTranslation("shop");
  const { orderId } = useParams<{ orderId: string }>();
  const orderQuery = useQuery({
    queryKey: ["shop", "order", orderId],
    queryFn: () => getShopOrder(orderId ?? ""),
    enabled: Boolean(orderId),
    refetchInterval: 30_000,
  });

  if (orderQuery.isPending) {
    return <p className="flex items-center gap-2 px-4 py-6 text-caption text-text-secondary"><Loader2 size={16} className="animate-spin" aria-hidden="true" />{t("checkout.placing")}</p>;
  }
  if (orderQuery.isError) {
    return <p role="alert" className="px-4 py-6 text-caption text-danger-text">{t("checkout.error")}</p>;
  }

  const order = orderQuery.data;
  const status = (KNOWN_STATUSES as readonly string[]).includes(order.status) ? order.status : "UNKNOWN";
  const stageIndex = ORDER_STAGES.indexOf(status as (typeof ORDER_STAGES)[number]);
  const cancelled = status === "CANCELLED";
  const paymentKey = ["COD", "MOMO", "BANK_TRANSFER"].includes(order.paymentMethod)
    ? order.paymentMethod
    : "UNKNOWN";

  return (
    <div className="mx-auto flex w-full max-w-5xl flex-col gap-5 px-4 py-5 lg:px-0">
      <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
        <div className="flex flex-wrap items-start justify-between gap-4">
          <div>
            <h1 className="text-[22px] font-bold text-text-primary">{t(cancelled ? "order.cancelledTitle" : "order.successTitle")}</h1>
            <p className="pt-1 text-[13px] text-text-secondary">{t("order.codeLabel")}: <span className="font-bold text-primary-dark">#{order.orderCode}</span></p>
          </div>
          <Link to="/shop" className="rounded-xl bg-primary-dark px-4 py-2.5 text-[12px] font-bold text-white hover:bg-primary">{t("order.continueShopping")}</Link>
        </div>
        <dl className="mt-4 grid gap-4 rounded-xl bg-background-alt/60 p-4 sm:grid-cols-3">
          <div><dt className="text-[10px] font-bold text-text-tertiary">{t("order.placedLabel")}</dt><dd className="pt-1 text-[13px] font-semibold text-text-primary">{new Date(order.createdAt).toLocaleString()}</dd></div>
          <div><dt className="text-[10px] font-bold text-text-tertiary">{t("order.methodLabel")}</dt><dd className="pt-1 text-[13px] font-semibold text-text-primary">{t(`order.payment.${paymentKey}`)}</dd></div>
          <div><dt className="text-[10px] font-bold text-text-tertiary">{t("order.statusLabel")}</dt><dd className="pt-1 text-[13px] font-bold text-primary-dark">{t(`order.status.${status}`)}</dd></div>
        </dl>
      </section>

      <section className="rounded-2xl bg-surface p-5 shadow-brand-md" aria-live="polite">
        <div className="flex items-center gap-3">
          <span className="flex size-9 items-center justify-center rounded-xl bg-chip-bg text-primary-dark"><Truck size={18} aria-hidden="true" /></span>
          <div><h2 className="text-[16px] font-bold text-text-primary">{t("order.trackingTitle")}</h2><p className="text-[11px] text-text-secondary">{t("order.trackingDisclaimer")}</p></div>
        </div>
        {cancelled ? (
          <p className="mt-5 flex items-center gap-2 rounded-xl bg-danger-bg p-4 text-small text-danger-text"><X size={16} aria-hidden="true" />{t("order.status.CANCELLED")}</p>
        ) : (
          <ol className="mt-6 grid gap-4 sm:grid-cols-5">
            {ORDER_STAGES.map((stage, index) => {
              const done = stageIndex >= index;
              const current = stageIndex === index;
              return <li key={stage} className="flex items-center gap-2 sm:flex-col sm:text-center">
                <span className={`flex size-8 shrink-0 items-center justify-center rounded-full ${done ? "bg-success text-white" : "bg-background-alt text-text-tertiary"}`}>
                  {done ? <Check size={15} aria-hidden="true" /> : current ? <Package size={15} aria-hidden="true" /> : <Circle size={12} aria-hidden="true" />}
                </span>
                <span className={`text-[11px] ${current ? "font-bold text-primary-dark" : "text-text-secondary"}`}>{t(`order.status.${stage}`)}</span>
              </li>;
            })}
          </ol>
        )}
      </section>

      <div className="grid gap-5 lg:grid-cols-[1fr_360px]">
        <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
          <h2 className="text-[16px] font-bold text-text-primary">{t("order.itemsTitle", { count: order.lines.length })}</h2>
          <ul className="mt-4 flex flex-col gap-3">
            {order.lines.map((line) => <li key={line.product.id} className="flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
              {line.product.imageUrl ? <img src={line.product.imageUrl} alt="" className="size-14 shrink-0 rounded-lg object-cover" /> : <span className="flex size-14 shrink-0 items-center justify-center rounded-lg bg-background-alt"><Package size={20} aria-hidden="true" /></span>}
              <div className="min-w-0 flex-1"><p className="font-semibold text-text-primary">{line.product.name}</p><p className="text-small text-text-secondary">{line.product.sku} · {t("checkout.qty", { count: line.quantity })}</p></div>
              <span className="shrink-0 text-small font-bold text-primary-dark">{formatVnd(line.totalVnd)}</span>
            </li>)}
          </ul>
          <dl className="mt-4 flex flex-col gap-2 border-t border-border pt-4 text-small">
            <div className="flex justify-between"><dt className="text-text-secondary">{t("order.subtotalLabel")}</dt><dd>{formatVnd(order.subtotalVnd)}</dd></div>
            {order.discountVnd > 0 ? <div className="flex justify-between"><dt className="text-text-secondary">{t("order.discountLabel")}</dt><dd>-{formatVnd(order.discountVnd)}</dd></div> : null}
            <div className="flex justify-between"><dt className="text-text-secondary">{t("order.shippingLabel")}</dt><dd>{order.shippingFeeVnd === 0 ? t("cartPanel.freeShipping") : formatVnd(order.shippingFeeVnd)}</dd></div>
            <div className="flex justify-between border-t border-border pt-3 text-body font-bold"><dt>{t("order.totalLabel")}</dt><dd className="text-primary-dark">{formatVnd(order.totalVnd)}</dd></div>
          </dl>
        </section>

        <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
          <h2 className="text-[16px] font-bold text-text-primary">{t("order.recipientTitle")}</h2>
          <dl className="mt-4 flex flex-col gap-3 text-small">
            <div><dt className="text-text-tertiary">{t("order.recipientLabel")}</dt><dd className="font-semibold text-text-primary">{order.receiverName}</dd></div>
            <div><dt className="text-text-tertiary">{t("checkout.receiverPhone")}</dt><dd className="font-semibold text-text-primary">{order.receiverPhone}</dd></div>
            <div><dt className="text-text-tertiary">{t("order.addressLabel")}</dt><dd className="font-semibold text-text-primary">{order.shippingAddress}</dd></div>
          </dl>
        </section>
      </div>
    </div>
  );
}
