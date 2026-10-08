import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { AlertTriangle, Lock, ShoppingBag, X } from "lucide-react";
import { SkeletonLoader } from "@/shared/ui";
import { useShopCart } from "@/features/shop";
import { exceedsStock, formatVnd, maxOrderQuantity } from "./shopFormat";
import { ProductGlyph, QuantityStepper, ShippingFee } from "./shopUi";
import { useCartLineActions } from "./useCartActions";

/**
 * Cột giỏ hàng bên phải trang Cửa hàng (design `Web - 14 & Giỏ hàng`).
 *
 * Mọi con số là phản hồi `GET/PUT/DELETE /api/v1/cart`: dòng hàng (tên, SKU, giá, số lượng,
 * `totalVnd` từng dòng), tạm tính, phí vận chuyển và tổng. Đã gỡ khỏi mockup vì backend không
 * có resource tương ứng: thanh tiến độ "miễn phí vận chuyển 2H", quà tặng 0đ, mã giảm giá,
 * nhãn "bảo mật SSL 256-bit"/"hoàn tiền nếu lỗi".
 */
export function CartPanel() {
  const { t } = useTranslation("shop");
  const cartQuery = useShopCart();
  const { pendingProductId, isUpdating, setQuantity, remove } = useCartLineActions();
  const cart = cartQuery.data;
  const lines = cart?.lines ?? [];
  const hasStockIssue = lines.some(exceedsStock);

  return (
    <aside className="flex flex-col gap-4 rounded-2xl bg-surface p-5 shadow-brand-lg xl:p-6">
      <div className="flex items-center justify-between gap-2">
        <h2 className="flex items-center gap-2 text-[17px] font-bold text-text-primary">
          <ShoppingBag size={18} className="text-primary-dark" aria-hidden="true" />
          {t("cartPanel.title")}
        </h2>
        {cart ? (
          <span className="shrink-0 rounded-full bg-chip-bg px-2.5 py-0.5 text-[11px] font-semibold text-primary-dark">
            {t("cartPanel.count", { count: lines.length })}
          </span>
        ) : null}
      </div>

      {cartQuery.isPending ? (
        <div className="flex flex-col gap-3" aria-hidden="true">
          <SkeletonLoader className="h-[76px] rounded-xl" />
          <SkeletonLoader className="h-[76px] rounded-xl" />
        </div>
      ) : cartQuery.isError ? (
        <div role="alert" className="flex flex-col items-center gap-2 py-4 text-center">
          <p className="text-[13px] text-danger-text">{t("cartPanel.error")}</p>
          <button
            type="button"
            onClick={() => {
              void cartQuery.refetch();
            }}
            className="rounded-lg px-3 py-2 text-[12px] font-semibold text-primary-dark hover:bg-background-alt"
          >
            {t("actions.retry", { ns: "common" })}
          </button>
        </div>
      ) : lines.length === 0 ? (
        <p className="py-6 text-center text-[13px] text-text-secondary">{t("cartPanel.empty")}</p>
      ) : (
        <>
          <ul className="flex flex-col gap-3">
            {lines.map((line) => {
              const { product } = line;
              const pending = pendingProductId === product.id;
              const outOfStock = product.stockQuantity <= 0;
              return (
                <li key={product.id} className="relative flex gap-3 rounded-xl bg-background-alt/60 p-2.5">
                  <ProductGlyph
                    sku={product.sku}
                    imageUrl={product.imageUrl}
                    className="size-14 shrink-0"
                    iconSize={22}
                  />
                  <div className="min-w-0 flex-1">
                    <Link
                      to={`/shop/products/${product.id}`}
                      className="line-clamp-2 pr-6 text-[13px] font-semibold leading-snug text-text-primary hover:text-primary-dark"
                    >
                      {product.name}
                    </Link>
                    <p className="truncate text-[11px] text-text-secondary">{product.sku}</p>
                    {exceedsStock(line) ? (
                      <p className="flex items-start gap-1 pt-0.5 text-[11px] font-semibold leading-snug text-danger-text">
                        <AlertTriangle size={11} className="mt-px shrink-0" aria-hidden="true" />
                        {outOfStock
                          ? t("product.outOfStock")
                          : t("cartPanel.onlyLeft", { count: product.stockQuantity })}
                      </p>
                    ) : null}
                    <div className="mt-1.5 flex flex-wrap items-center justify-between gap-x-2 gap-y-1">
                      <QuantityStepper
                        size="sm"
                        value={line.quantity}
                        max={maxOrderQuantity(product.stockQuantity)}
                        pending={pending}
                        onChange={(next) => {
                          setQuantity(product.id, next);
                        }}
                      />
                      <span className="text-[13px] font-bold text-primary-dark">{formatVnd(line.totalVnd)}</span>
                    </div>
                  </div>
                  <button
                    type="button"
                    aria-label={t("cartPanel.removeItem", { name: product.name })}
                    disabled={pending}
                    onClick={() => {
                      remove(product.id);
                    }}
                    className="absolute right-1 top-1 flex size-7 items-center justify-center rounded-md text-text-tertiary hover:bg-surface hover:text-danger disabled:opacity-40"
                  >
                    <X size={14} aria-hidden="true" />
                  </button>
                </li>
              );
            })}
          </ul>

          <dl
            className={`flex flex-col gap-1.5 border-t border-border pt-3 text-[13px] transition-opacity ${isUpdating ? "opacity-60" : ""}`}
            aria-busy={isUpdating || undefined}
          >
            <div className="flex justify-between gap-3">
              <dt className="text-text-secondary">{t("cartPanel.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(cart?.subtotalVnd ?? 0)}</dd>
            </div>
            <div className="flex justify-between gap-3">
              <dt className="text-text-secondary">{t("cartPanel.shippingLabel")}</dt>
              <dd className="text-right font-semibold text-text-primary">
                <ShippingFee value={cart?.shippingFeeVnd ?? 0} />
              </dd>
            </div>
          </dl>

          <div
            className={`flex items-end justify-between gap-3 border-t border-border pt-3 transition-opacity ${isUpdating ? "opacity-60" : ""}`}
          >
            <span className="text-[15px] font-bold text-text-primary">{t("cartPanel.totalLabel")}</span>
            <span className="text-[22px] font-bold leading-7 text-primary-dark">{formatVnd(cart?.totalVnd ?? 0)}</span>
          </div>

          {hasStockIssue ? (
            <p className="rounded-lg bg-danger-bg px-3 py-2 text-[12px] leading-relaxed text-danger-text">
              {t("cartPage.stockIssue")}
            </p>
          ) : null}

          {hasStockIssue ? (
            <Link
              to="/cart"
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-chip-bg px-4 py-3 text-[14px] font-bold text-primary-dark hover:bg-info"
            >
              {t("cartPanel.reviewCart")}
            </Link>
          ) : (
            <Link
              to="/checkout"
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-primary-dark px-4 py-3 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
            >
              <Lock size={15} aria-hidden="true" />
              {t("cartPanel.checkout")}
            </Link>
          )}
        </>
      )}
    </aside>
  );
}
