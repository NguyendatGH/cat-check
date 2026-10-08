import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import { ChevronRight, Info, Loader2, PackageSearch, ShoppingCart } from "lucide-react";
import { isApiError } from "@/shared/api";
import { cn } from "@/shared/lib/cn";
import { EmptyState, ErrorState, SkeletonLoader } from "@/shared/ui";
import { isShopId, useShopProduct, useShopProducts, type ShopProductApi } from "@/features/shop";
import { discountPercent, formatVnd, maxOrderQuantity } from "./shopFormat";
import { CareNoteCard, CartShortcut, PhBandScale, PriceRow, ProductGlyph, QuantityStepper, StockChip } from "./shopUi";
import { useAddToCart } from "./useCartActions";

/**
 * `/shop/products/:productId` — Chi tiết sản phẩm (design `Web - 14b` / mobile `14.a`).
 *
 * CHỈ hiển thị field `GET /api/v1/shop/products/{id}` trả thật: `sku`, `name`, `description`,
 * `imageUrl`, `priceVnd`, `compareAtPriceVnd`, `stockQuantity`.
 *
 * Đã gỡ khỏi mockup (tuyên bố chưa kiểm chứng hoặc không có API): gallery ảnh, điểm/số lượt
 * đánh giá và review, "120+ phòng khám khuyên dùng", chip freeship/giao 2H, lựa chọn combo/
 * size/định kỳ, chip "100% đậu nành/khử mùi 99%/không bụi 99,9%", khối "cơ chế phát hiện bệnh"
 * (thay bằng dải pH tham chiếu của API ở cùng vị trí), "hướng dẫn 4 bước", hộp chat bác sĩ.
 * Bảng "So sánh các gói" giữ vị trí nhưng chỉ so các field thật của catalogue (giá, giá gốc,
 * tồn kho).
 */

function DetailSkeleton() {
  return (
    <div className="flex flex-col gap-6 px-4 py-5 lg:flex-row lg:px-0 lg:py-0" aria-hidden="true">
      <SkeletonLoader className="aspect-[4/3] w-full rounded-2xl lg:w-[42%]" />
      <div className="flex flex-1 flex-col gap-3">
        <SkeletonLoader className="h-6 w-40 rounded-full" />
        <SkeletonLoader className="h-9 w-3/4 rounded-lg" />
        <SkeletonLoader className="h-4 w-full rounded" />
        <SkeletonLoader className="h-24 w-full rounded-2xl" />
      </div>
    </div>
  );
}

/** Bảng so sánh các sản phẩm của catalogue — cột là sản phẩm, hàng là field thật của API. */
function CatalogCompare({ current, products }: { current: ShopProductApi; products: ShopProductApi[] }) {
  const { t } = useTranslation("shop");
  const rows: { key: string; render: (p: ShopProductApi) => string }[] = [
    { key: "price", render: (p) => formatVnd(p.priceVnd) },
    {
      key: "compareAt",
      render: (p) => (p.compareAtPriceVnd && p.compareAtPriceVnd > p.priceVnd ? formatVnd(p.compareAtPriceVnd) : "—"),
    },
    {
      key: "discount",
      render: (p) => {
        const percent = discountPercent(p.priceVnd, p.compareAtPriceVnd);
        return percent > 0 ? t("product.discount", { percent }) : "—";
      },
    },
    {
      key: "stock",
      render: (p) =>
        p.stockQuantity > 0 ? t("compare.stockValue", { count: p.stockQuantity }) : t("product.outOfStock"),
    },
  ];

  return (
    <section className="rounded-2xl bg-surface p-5 shadow-brand-md md:p-6">
      <h2 className="text-[18px] font-bold text-text-primary md:text-[20px]">{t("compare.title")}</h2>
      <p className="pt-1 text-[12px] text-text-secondary">{t("compare.subtitle")}</p>

      {/* Mobile: danh sách thẻ (bảng nhiều cột không đọc được ở 390px). */}
      <ul className="flex flex-col gap-3 pt-4 md:hidden">
        {products.map((p) => {
          const isCurrent = p.id === current.id;
          return (
            <li
              key={p.id}
              className={cn(
                "flex gap-3 rounded-xl p-3",
                isCurrent ? "bg-chip-bg/60 ring-1 ring-primary/30" : "bg-background-alt/60",
              )}
            >
              <ProductGlyph sku={p.sku} imageUrl={p.imageUrl} className="size-14 shrink-0" iconSize={22} />
              <div className="min-w-0 flex-1">
                {isCurrent ? (
                  <p className="text-[13px] font-bold leading-snug text-text-primary">{p.name}</p>
                ) : (
                  <Link
                    to={`/shop/products/${p.id}`}
                    className="block text-[13px] font-bold leading-snug text-text-primary hover:text-primary-dark"
                  >
                    {p.name}
                  </Link>
                )}
                <PriceRow product={p} className="pt-1" />
                <div className="pt-1.5">
                  <StockChip stockQuantity={p.stockQuantity} />
                </div>
              </div>
            </li>
          );
        })}
      </ul>

      {/* Web: bảng so sánh, cột sản phẩm đang xem được tô như cột "khuyên dùng" của design. */}
      <div className="hidden pt-5 md:block">
        <table className="w-full table-fixed border-separate border-spacing-0 overflow-hidden rounded-xl text-[13px]">
          <thead>
            <tr className="bg-background-alt/70">
              <th scope="col" className="w-[22%] px-4 py-3 text-left text-[12px] font-semibold text-text-secondary">
                {t("compare.attribute")}
              </th>
              {products.map((p) => (
                <th
                  key={p.id}
                  scope="col"
                  className={cn(
                    "px-4 py-3 text-left align-top text-[13px] font-bold leading-snug",
                    p.id === current.id ? "text-primary-dark" : "text-text-primary",
                  )}
                >
                  {p.id === current.id ? (
                    <span className="block">{p.name}</span>
                  ) : (
                    <Link to={`/shop/products/${p.id}`} className="block hover:text-primary-dark hover:underline">
                      {p.name}
                    </Link>
                  )}
                  <span className="block pt-0.5 text-[11px] font-medium text-text-tertiary">
                    {p.id === current.id ? t("compare.viewing") : p.sku}
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.key}>
                <th scope="row" className="border-t border-border px-4 py-3 text-left font-medium text-text-secondary">
                  {t(`compare.rows.${row.key}`)}
                </th>
                {products.map((p) => (
                  <td
                    key={p.id}
                    className={cn(
                      "border-t border-border px-4 py-3",
                      p.id === current.id ? "bg-chip-bg/40 font-bold text-primary-dark" : "text-text-primary",
                    )}
                  >
                    {row.render(p)}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export function ProductDetailPage() {
  const { productId } = useParams<{ productId: string }>();
  // `key` theo sản phẩm: chuyển sang sản phẩm khác (bảng so sánh) thì số lượng chọn về 1.
  return <ProductDetailView key={productId} productId={productId} />;
}

function ProductDetailView({ productId }: { productId: string | undefined }) {
  const { t } = useTranslation("shop");
  const validId = isShopId(productId);
  const productQuery = useShopProduct(productId);
  const productsQuery = useShopProducts();
  const { add, pendingProductId } = useAddToCart();
  const [quantity, setQuantity] = useState(1);

  const notFound = !validId || (isApiError(productQuery.error) && productQuery.error.status === 404);

  if (notFound) {
    return (
      <EmptyState
        icon={<PackageSearch size={22} />}
        title={t("detail.notFound")}
        description={t("detail.notFoundBody")}
        action={
          <Link
            to="/shop"
            className="inline-flex rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
          >
            {t("detail.backToShopCta")}
          </Link>
        }
        className="mx-4 mt-5 rounded-2xl bg-surface shadow-brand-md lg:mx-0 lg:mt-0"
      />
    );
  }
  if (productQuery.isPending) return <DetailSkeleton />;
  if (productQuery.isError) {
    return (
      <ErrorState
        title={t("detail.error")}
        onRetry={() => {
          void productQuery.refetch();
        }}
        className="mx-4 mt-5 rounded-2xl bg-surface shadow-brand-md lg:mx-0 lg:mt-0"
      />
    );
  }

  const product = productQuery.data;
  const percent = discountPercent(product.priceVnd, product.compareAtPriceVnd);
  const maxQuantity = maxOrderQuantity(product.stockQuantity);
  const outOfStock = maxQuantity === 0;
  const selected = Math.min(quantity, Math.max(1, maxQuantity));
  const pending = pendingProductId === product.id;
  const catalog = productsQuery.data ?? [];

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0 lg:py-0">
      {/* Breadcrumb — hai mức thật: cửa hàng và tên sản phẩm của API. */}
      <nav
        aria-label={t("detail.breadcrumb")}
        className="flex flex-wrap items-center gap-1 text-[12px] text-text-secondary"
      >
        <Link to="/shop" className="hover:text-primary-dark">
          {t("detail.backToShop")}
        </Link>
        <ChevronRight size={12} aria-hidden="true" />
        <span className="font-semibold text-text-primary">{product.name}</span>
      </nav>

      <div className="flex flex-col gap-5 lg:flex-row lg:items-start lg:gap-8">
        {/* Cột trái: ảnh thật của API nếu có, không thì ô biểu tượng theo SKU */}
        <ProductGlyph
          sku={product.sku}
          imageUrl={product.imageUrl}
          className="aspect-[16/10] w-full rounded-2xl shadow-brand-md md:aspect-[16/9] lg:aspect-[4/3] lg:w-[42%] lg:shrink-0"
          iconSize={72}
          showSku
        />

        {/* Cột phải: thông tin mua hàng */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="inline-flex rounded-full bg-chip-bg px-2.5 py-1 text-[11px] font-bold text-primary-dark">
              {t("product.skuLabel", { sku: product.sku })}
            </span>
            <StockChip stockQuantity={product.stockQuantity} />
          </div>
          <h1 className="pt-3 text-[24px] font-bold leading-tight text-text-primary lg:text-[30px]">{product.name}</h1>
          <p className="pt-2.5 text-[14px] leading-relaxed text-text-secondary">{product.description}</p>

          <div className="mt-4 rounded-2xl bg-surface p-4 shadow-brand-md">
            <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
              <span className="text-[28px] font-bold leading-none text-primary-dark">
                {formatVnd(product.priceVnd)}
              </span>
              {product.compareAtPriceVnd && product.compareAtPriceVnd > product.priceVnd ? (
                <s className="text-[14px] text-text-tertiary">{formatVnd(product.compareAtPriceVnd)}</s>
              ) : null}
              {percent > 0 ? (
                <span className="rounded-lg bg-danger-bg px-2 py-1 text-[11px] font-bold text-danger-text">
                  {t("product.discount", { percent })}
                </span>
              ) : null}
            </span>
            <p className="flex items-start gap-1.5 pt-2.5 text-[12px] leading-relaxed text-text-tertiary">
              <Info size={13} className="mt-0.5 shrink-0" aria-hidden="true" />
              {t("detail.priceNote")}
            </p>
          </div>

          {/* Mua: số lượng (trần = tồn kho thật, tối đa 99) + thêm giỏ + mua ngay. Đặt
              trong luồng trang thay vì thanh dính đáy: bottom nav của AppLayout (có nút "Quét"
              nhô lên) sẽ che thanh dính ở mobile. */}
          <div className="flex items-center gap-2 pt-4">
            <QuantityStepper
              value={selected}
              max={Math.max(1, maxQuantity)}
              onChange={setQuantity}
              className={outOfStock ? "pointer-events-none opacity-50" : undefined}
            />
            <button
              type="button"
              disabled={outOfStock || pending}
              onClick={() => {
                void add(product, selected);
              }}
              aria-label={t("detail.addToCart")}
              className="flex h-11 shrink-0 items-center justify-center gap-1.5 rounded-xl bg-chip-bg px-3 text-[13px] font-semibold text-primary-dark hover:bg-info disabled:cursor-not-allowed disabled:opacity-50 md:flex-1 md:px-4"
            >
              {pending ? (
                <Loader2 size={16} className="animate-spin" aria-hidden="true" />
              ) : (
                <ShoppingCart size={16} aria-hidden="true" />
              )}
              <span className="hidden md:inline">{t("detail.addToCart")}</span>
            </button>
            <button
              type="button"
              disabled={outOfStock || pending}
              onClick={() => {
                void add(product, selected, { goToCheckout: true });
              }}
              className="flex h-11 min-w-0 flex-1 items-center justify-center rounded-xl bg-primary-dark px-4 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
            >
              <span className="truncate">
                {outOfStock
                  ? t("product.outOfStock")
                  : t("detail.buyNowWithPrice", { price: formatVnd(product.priceVnd * selected) })}
              </span>
            </button>
          </div>
          {!outOfStock && selected >= maxQuantity ? (
            <p className="pt-2 text-[12px] text-text-tertiary">{t("detail.maxQuantity", { count: maxQuantity })}</p>
          ) : null}

          <CartShortcut className="mt-4" />
        </div>
      </div>

      {/* Vị trí "Cơ chế phát hiện" của design: dải pH tham chiếu từ `GET /reference/ph-bands`. */}
      <PhBandScale />

      {catalog.length > 1 ? <CatalogCompare current={product} products={catalog} /> : null}

      <CareNoteCard />
    </div>
  );
}
