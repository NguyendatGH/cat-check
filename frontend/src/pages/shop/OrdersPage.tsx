import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ChevronRight, ClipboardList } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { formatDate } from "@/shared/lib/format/formatDate";
import { EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { useMyOrders } from "@/features/shop";
import { formatVnd } from "./shopFormat";

export function OrdersPage() {
  const { t } = useTranslation("shop");
  const ordersQuery = useMyOrders();

  if (ordersQuery.isPending) return <SkeletonLoader className="h-[240px] rounded-2xl" />;
  if (ordersQuery.isError) {
    return (
      <ErrorState
        title={t("myOrders.error")}
        onRetry={() => {
          void ordersQuery.refetch();
        }}
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    );
  }
  const orders = ordersQuery.data;
  if (orders.length === 0) {
    return (
      <EmptyState
        icon={<ClipboardList size={22} />}
        title={t("myOrders.empty.title")}
        description={t("myOrders.empty.description")}
        action={
          <Link
            to="/shop"
            className="inline-flex rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
          >
            {t("myOrders.empty.cta")}
          </Link>
        }
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    );
  }

  return (
    <div className="flex flex-col gap-4">
      <h1 className="text-[22px] font-bold leading-tight text-text-primary md:text-[26px]">{t("myOrders.title")}</h1>
      <ul className="flex flex-col gap-3">
        {orders.map((order) => (
          <li key={order.id}>
            <Link
              to={`/orders/${order.id}`}
              className="flex items-center gap-3 rounded-2xl bg-surface p-4 shadow-brand-md hover:bg-background-alt"
            >
              <span className="min-w-0 flex-1">
                <span className="block truncate text-[14px] font-bold text-primary-dark">{`#${order.orderCode}`}</span>
                <span className="block pt-0.5 text-[12px] text-text-secondary">
                  {`${formatDate(order.createdAt, "HH:mm, dd/MM/yyyy")} · ${t("myOrders.itemCount", { count: order.lines.length })}`}
                </span>
              </span>
              <span className="flex shrink-0 flex-col items-end gap-1">
                <span
                  className={cn(
                    "rounded-full px-2.5 py-0.5 text-[12px] font-bold",
                    order.status === "CANCELLED"
                      ? "bg-danger-bg text-danger-text"
                      : order.status === "DELIVERED"
                        ? "bg-success-bg text-success-text"
                        : "bg-chip-bg text-primary-dark",
                  )}
                >
                  {t(`order.status.${order.status}`, { defaultValue: t("order.status.UNKNOWN") })}
                </span>
                <span className="text-[14px] font-bold text-text-primary">{formatVnd(order.totalVnd)}</span>
              </span>
              <ChevronRight size={16} className="shrink-0 text-text-tertiary" aria-hidden="true" />
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
