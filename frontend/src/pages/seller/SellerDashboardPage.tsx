import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import { AlertTriangle, EyeOff, FileEdit, Store, type LucideIcon } from "lucide-react";
import { Card, ErrorState, SkeletonLoader } from "@/shared/ui";

import { formatProductVnd, useAdminProducts } from "@/features/admin";
import { LOW_STOCK_THRESHOLD, countProducts, lowStockProducts } from "@/features/seller";

/** Trùng ROUTE_PATTERNS.sellerProducts (pages không được import từ app/). */
const SELLER_PRODUCTS_PATH = "/seller/products";

function Kpi({ icon: Icon, label, value, hint }: { icon: LucideIcon; label: string; value: number; hint?: string }) {
  return (
    <Card className="flex flex-col gap-1 p-4">
      <div className="flex items-center gap-2 text-caption font-semibold text-text-secondary">
        <Icon className="size-4" aria-hidden="true" />
        {label}
      </div>
      <p className="text-h2 text-text-primary">{value}</p>
      {hint ? <p className="text-small text-text-tertiary">{hint}</p> : null}
    </Card>
  );
}

export function SellerDashboardPage() {
  const { t } = useTranslation("seller");
  const products = useAdminProducts();

  return (
    <div className="flex flex-col gap-5">
      <header>
        <h1 className="text-h2 text-text-primary">{t("dashboard.title")}</h1>
        <p className="text-caption text-text-secondary">{t("dashboard.description")}</p>
      </header>

      {products.isPending ? (
        <div className="grid grid-cols-2 gap-3 lg:grid-cols-4" role="status" aria-label="Đang tải">
          {[0, 1, 2, 3].map((i) => (
            <SkeletonLoader key={i} className="h-24" />
          ))}
        </div>
      ) : null}
      {products.isError ? (
        <ErrorState
          title={t("dashboard.loadError")}
          onRetry={() => {
            void products.refetch();
          }}
        />
      ) : null}

      {products.data
        ? (() => {
            const counts = countProducts(products.data);
            const low = lowStockProducts(products.data);
            return (
              <>
                <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
                  <Kpi icon={Store} label={t("dashboard.published")} value={counts.published} />
                  <Kpi icon={FileEdit} label={t("dashboard.draft")} value={counts.draft} />
                  <Kpi icon={EyeOff} label={t("dashboard.archived")} value={counts.archived} />
                  <Kpi
                    icon={AlertTriangle}
                    label={t("dashboard.lowStock")}
                    value={counts.lowStock}
                    hint={t("dashboard.lowStockHint", { threshold: LOW_STOCK_THRESHOLD })}
                  />
                </div>

                <Card className="flex flex-col gap-3 p-4">
                  <h2 className="text-h3 text-text-primary">{t("dashboard.lowStockTitle")}</h2>
                  {low.length === 0 ? (
                    <p className="text-caption text-text-secondary">{t("dashboard.lowStockEmpty")}</p>
                  ) : (
                    <ul className="divide-y divide-border">
                      {low.slice(0, 8).map((p) => (
                        <li key={p.id} className="flex items-center justify-between gap-3 py-2 text-caption">
                          <Link
                            to={SELLER_PRODUCTS_PATH}
                            className="min-w-0 truncate font-semibold text-primary hover:underline"
                          >
                            {p.name} <span className="font-normal text-text-tertiary">({p.sku})</span>
                          </Link>
                          <span className="shrink-0 text-text-secondary">
                            {t("dashboard.stock", { count: p.stockQuantity })} · {formatProductVnd(p.priceVnd)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  )}
                  <Link to={SELLER_PRODUCTS_PATH} className="text-caption font-semibold text-primary hover:underline">
                    {t("dashboard.manage")}
                  </Link>
                </Card>
              </>
            );
          })()
        : null}

      <p className="text-caption text-text-secondary">{t("dashboard.orders")}</p>
    </div>
  );
}
