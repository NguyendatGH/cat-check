import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Badge, Button } from "@/shared/ui";
import {
  ADMIN_ORDER_STATUSES,
  AdminPageHeader,
  AdminSection,
  AdminSelect,
  ApiErrorNote,
  DenseTableFrame,
  ReasonConfirmDialog,
  denseRowClass,
  denseTableClass,
  denseTdClass,
  denseThClass,
  formatAdminDateTime,
  formatProductVnd as formatVnd,
  useAdminOrders,
  useChangeAdminOrderStatus,
} from "@/features/admin";
import type { AdminOrder } from "@/features/admin";

// shortcut: the API returns no total, so "next" stays enabled on an exact multiple of 20 and the last page may be empty.
const PAGE_SIZE = 20;

export function AdminOrdersPage() {
  const { t } = useTranslation("admin");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [cancelling, setCancelling] = useState<AdminOrder | null>(null);
  const orders = useAdminOrders(status === "" ? undefined : status, page);
  const change = useChangeAdminOrderStatus();

  function move(order: AdminOrder, next: string, reason?: string) {
    if (next === "CANCELLED" && reason === undefined) {
      change.reset();
      setCancelling(order);
      return;
    }
    change.mutate({ id: order.id, status: next, reason }, {
      onSuccess: () => {
        setCancelling(null);
      },
    });
  }

  const data = orders.data;
  return (
    <div className="flex flex-col gap-5">
      <AdminPageHeader title={t("pages.orders.title")} description={t("pages.orders.description")} />

      <AdminSection title={t("orderAdmin.listTitle")} description={t("orderAdmin.listDescription")}>
        <AdminSelect
          label={t("orderAdmin.filterLabel")}
          value={status}
          onChange={(event) => {
            setStatus(event.target.value);
            setPage(0);
          }}
        >
          <option value="">{t("orderAdmin.filterAll")}</option>
          {ADMIN_ORDER_STATUSES.map((value) => (
            <option key={value} value={value}>
              {t(`orderAdmin.statuses.${value}`)}
            </option>
          ))}
        </AdminSelect>
        {orders.isPending ? <p className="text-caption text-text-secondary">{t("feedback.loading")}</p> : null}
        {orders.isError ? <ApiErrorNote error={orders.error} /> : null}
        {cancelling === null && change.error ? <ApiErrorNote error={change.error} /> : null}
        {data && data.length === 0 ? <p className="text-caption text-text-secondary">{t("feedback.empty")}</p> : null}
        {data && data.length > 0 ? (
          <DenseTableFrame label={t("orderAdmin.listTitle")}>
            <table className={denseTableClass}>
              <thead>
                <tr>
                  <th className={denseThClass}>{t("orderAdmin.code")}</th>
                  <th className={denseThClass}>{t("orderAdmin.receiver")}</th>
                  <th className={denseThClass}>{t("orderAdmin.items")}</th>
                  <th className={denseThClass}>{t("orderAdmin.payment")}</th>
                  <th className={denseThClass}>{t("orderAdmin.total")}</th>
                  <th className={denseThClass}>{t("orderAdmin.status")}</th>
                  <th className={denseThClass}>{t("orderAdmin.action")}</th>
                </tr>
              </thead>
              <tbody>
                {data.map((order) => (
                  <tr key={order.id} className={denseRowClass}>
                    <td className={denseTdClass}>
                      <code>{order.orderCode}</code>
                      <span className="block text-small text-text-tertiary">{formatAdminDateTime(order.createdAt)}</span>
                    </td>
                    <td className={denseTdClass}>
                      {order.receiverName}
                      <span className="block text-small text-text-tertiary">{order.receiverPhone}</span>
                      <span className="block max-w-64 whitespace-normal break-words text-small text-text-tertiary">
                        {order.shippingAddress}
                      </span>
                    </td>
                    <td className={denseTdClass}>
                      <ul>
                        {order.lines.map((line) => (
                          <li key={line.product.id}>
                            {line.product.name} × {line.quantity}
                          </li>
                        ))}
                      </ul>
                    </td>
                    <td className={denseTdClass}>
                      {t(`orderAdmin.paymentMethods.${order.paymentMethod}`, { defaultValue: order.paymentMethod })}
                    </td>
                    <td className={denseTdClass}>{formatVnd(order.totalVnd)}</td>
                    <td className={denseTdClass}>
                      <Badge tone={order.status === "CANCELLED" ? "danger" : "info"}>
                        {t(`orderAdmin.statuses.${order.status}`, { defaultValue: order.status })}
                      </Badge>
                      {order.refundNeeded ? (
                        <Badge tone="warning" className="ml-1">
                          {t("orderAdmin.refundNeeded")}
                        </Badge>
                      ) : null}
                      {order.cancelReason ? (
                        <span className="block max-w-64 whitespace-normal break-words text-small text-text-tertiary">
                          {t("orderAdmin.cancelReason", { reason: order.cancelReason })}
                        </span>
                      ) : null}
                    </td>
                    <td className={denseTdClass}>
                      <div className="flex flex-wrap gap-1">
                        {order.allowedNextStatuses.map((next) => (
                          <Button
                            key={next}
                            variant="tertiary"
                            size="sm"
                            disabled={change.isPending}
                            onClick={() => {
                              move(order, next);
                            }}
                          >
                            {t(`orderAdmin.actions.${next}`, { defaultValue: next })}
                          </Button>
                        ))}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </DenseTableFrame>
        ) : null}
        <div className="flex items-center justify-between gap-3">
          <Button
            variant="tertiary"
            size="sm"
            disabled={page === 0}
            onClick={() => {
              setPage(page - 1);
            }}
          >
            {t("pager.previous")}
          </Button>
          <span className="text-caption text-text-secondary">{t("orderAdmin.page", { page: page + 1 })}</span>
          <Button
            variant="tertiary"
            size="sm"
            disabled={!data || data.length < PAGE_SIZE}
            onClick={() => {
              setPage(page + 1);
            }}
          >
            {t("pager.next")}
          </Button>
        </div>
      </AdminSection>

      <ReasonConfirmDialog
        open={cancelling !== null}
        onOpenChange={(open) => {
          if (!open) {
            setCancelling(null);
            change.reset();
          }
        }}
        title={t("orderAdmin.cancelTitle")}
        target={cancelling?.orderCode ?? ""}
        consequence={t("orderAdmin.cancelConsequence")}
        confirmLabel={t("orderAdmin.cancelConfirm")}
        destructive
        pending={change.isPending}
        error={change.error}
        onConfirm={(reason) => {
          if (cancelling) move(cancelling, "CANCELLED", reason);
        }}
      />
    </div>
  );
}
