import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { ArrowDownUp, ArrowRight, Boxes, Loader2, ShoppingCart, Tag } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { ErrorState, SkeletonLoader } from "@/shared/ui";
import { useShopProducts, type ShopProductApi } from "@/features/shop";
import { CartPanel } from "./CartPanel";
import { discountPercent, formatVnd } from "./shopFormat";
import { BuyingFacts, CareNoteCard, CartShortcut, PhBandScale, PriceRow, ProductGlyph, StockChip } from "./shopUi";
import { useAddToCart } from "./useCartActions";

/**
 * `/shop` — Cửa hàng CatCheck (design `Web - 14 & Giỏ hàng` / mobile `14. Cửa hàng`).
 *
 * Hai layout responsive (mốc `md`, vì token `--breakpoint-sm` của dự án là 375px nên `sm:`
 * bật ngay trên điện thoại):
 *   - từ `md`: hero hai cột + lưới sản phẩm; từ `lg` thêm cột giỏ hàng dính bên phải;
 *   - dưới `md`: hero thẻ xanh đặc + danh sách sản phẩm dạng hàng ngang, không có cột giỏ
 *     hàng (đã có route `/cart` riêng).
 *
 * NỘI DUNG: chỉ hiển thị field `GET /api/v1/shop/products` trả thật (`sku`, `name`,
 * `description`, `imageUrl`, `priceVnd`, `compareAtPriceVnd`, `stockQuantity`). API không có
 * xếp hạng, đánh giá, thành phần hay chứng nhận ⇒ UI không hiển thị những thứ đó. Banner
 * "chính sách bác sĩ thú y…" đầu trang, ảnh hero, chip quà tặng/freeship, khối "tư vấn với bác
 * sĩ" và "cam kết an toàn tuyệt đối" của mockup đã bỏ (tuyên bố chưa kiểm chứng / không có API).
 * Nhãn phân loại pH đến từ `entities/ph-bands`.
 *
 * Khung app (sidebar trái + thanh tìm kiếm trên) thuộc `AppLayout`, KHÔNG dựng ở đây.
 */

const FILTERS = ["all", "subscription", "single"] as const;
type FilterKey = (typeof FILTERS)[number];

const CATALOG_ANCHOR = "shop-catalog";

/** Gói định kỳ nhận ra bằng họ SKU thật của catalogue, không bằng nhãn marketing. */
function isSubscription(sku: string): boolean {
  return sku.startsWith("SUBSCRIPTION");
}

/** Nút thêm giỏ dùng chung: khoá khi hết hàng, spinner khi đang chờ máy chủ. */
function AddToCartButton({
  product,
  className,
  iconSize,
  label,
}: {
  product: ShopProductApi;
  className: string;
  iconSize: number;
  label: string;
}) {
  const { add, pendingProductId } = useAddToCart();
  const pending = pendingProductId === product.id;
  const outOfStock = product.stockQuantity <= 0;
  return (
    <button
      type="button"
      disabled={outOfStock || pending}
      onClick={() => {
        void add(product, 1);
      }}
      className={className}
    >
      {pending ? (
        <Loader2 size={iconSize} className="animate-spin" aria-hidden="true" />
      ) : (
        <ShoppingCart size={iconSize} aria-hidden="true" />
      )}
      {label}
    </button>
  );
}

/** Thẻ sản phẩm bản mobile: ô hình vuông bên trái, giá + nút thêm giỏ ở hàng dưới. */
function MobileProductCard({ product }: { product: ShopProductApi }) {
  const { t } = useTranslation("shop");
  const outOfStock = product.stockQuantity <= 0;

  return (
    <article className="rounded-2xl bg-surface p-3 shadow-brand-md">
      <div className="flex gap-3">
        <Link to={`/shop/products/${product.id}`} className="shrink-0" tabIndex={-1} aria-hidden="true">
          <ProductGlyph sku={product.sku} imageUrl={product.imageUrl} className="size-[88px]" iconSize={32} />
        </Link>
        <div className="min-w-0 flex-1">
          <span className="inline-flex max-w-full truncate rounded-full bg-chip-bg px-2.5 py-0.5 text-[11px] font-bold text-primary-dark">
            {t("product.skuLabel", { sku: product.sku })}
          </span>
          <Link
            to={`/shop/products/${product.id}`}
            className="block pt-1 text-[15px] font-bold leading-snug text-text-primary hover:text-primary-dark"
          >
            {product.name}
          </Link>
          <p className="line-clamp-2 pt-0.5 text-[12px] leading-relaxed text-text-secondary">{product.description}</p>
          <div className="pt-1.5">
            <StockChip stockQuantity={product.stockQuantity} />
          </div>
        </div>
      </div>

      <div className="flex items-end justify-between gap-3 pt-3">
        <PriceRow product={product} className="min-w-0" />
        <AddToCartButton
          product={product}
          iconSize={15}
          label={outOfStock ? t("product.outOfStock") : t("product.addToCartLong")}
          className="flex shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-4 py-3 text-[13px] font-bold text-white shadow-brand-md hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
        />
      </div>
    </article>
  );
}

function ProductCard({ product }: { product: ShopProductApi }) {
  const { t } = useTranslation("shop");
  const { add, pendingProductId } = useAddToCart();
  const outOfStock = product.stockQuantity <= 0;
  const pending = pendingProductId === product.id;

  return (
    <article className="flex flex-col rounded-2xl bg-surface p-3 shadow-brand-md">
      <Link to={`/shop/products/${product.id}`} tabIndex={-1} aria-hidden="true">
        <ProductGlyph
          sku={product.sku}
          imageUrl={product.imageUrl}
          className="aspect-[164/150] w-full"
          iconSize={44}
          showSku
        />
      </Link>

      <Link
        to={`/shop/products/${product.id}`}
        className="pt-3 text-[15px] font-bold leading-snug text-text-primary hover:text-primary-dark"
      >
        {product.name}
      </Link>
      <p className="line-clamp-2 pt-1 text-[12px] leading-relaxed text-text-secondary">{product.description}</p>
      <div className="pt-2">
        <StockChip stockQuantity={product.stockQuantity} />
      </div>

      <PriceRow product={product} className="mt-auto pt-3" />

      <div className="flex gap-1.5 pt-3">
        <AddToCartButton
          product={product}
          iconSize={13}
          label={t("product.addToCart")}
          className="flex flex-1 items-center justify-center gap-1 whitespace-nowrap rounded-xl bg-chip-bg px-1.5 py-3 text-[12px] font-semibold text-primary-dark hover:bg-info disabled:cursor-not-allowed disabled:opacity-50"
        />
        <button
          type="button"
          disabled={outOfStock || pending}
          onClick={() => {
            void add(product, 1, { goToCheckout: true });
          }}
          className="flex-1 whitespace-nowrap rounded-xl bg-primary-dark px-1.5 py-3 text-[12px] font-semibold text-white hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
        >
          {outOfStock ? t("product.outOfStock") : t("product.buyNow")}
        </button>
      </div>
    </article>
  );
}

function CatalogSkeleton() {
  return (
    <>
      <div className="flex flex-col gap-3 md:hidden" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <SkeletonLoader key={i} className="h-[170px] rounded-2xl" />
        ))}
      </div>
      <div className="hidden gap-4 md:grid md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <SkeletonLoader key={i} className="h-[400px] rounded-2xl" />
        ))}
      </div>
    </>
  );
}

export function ShopPage() {
  const { t } = useTranslation("shop");
  const [activeFilter, setActiveFilter] = useState<FilterKey>("all");
  const [priceAscending, setPriceAscending] = useState(true);
  const productsQuery = useShopProducts();
  // `useMemo` chứ không phải `?? []` tại chỗ: mảng mới mỗi render sẽ làm memo lọc/sắp xếp
  // bên dưới chạy lại liên tục (react-hooks/exhaustive-deps).
  const products = useMemo(() => productsQuery.data ?? [], [productsQuery.data]);

  const visibleProducts = useMemo(() => {
    const matching = products.filter((product) =>
      activeFilter === "all"
        ? true
        : activeFilter === "subscription"
          ? isSubscription(product.sku)
          : !isSubscription(product.sku),
    );
    return matching.slice().sort((a, b) => (priceAscending ? a.priceVnd - b.priceVnd : b.priceVnd - a.priceVnd));
  }, [products, activeFilter, priceAscending]);

  const lowestPrice = products.length > 0 ? Math.min(...products.map((p) => p.priceVnd)) : null;
  const bestDiscount = products.reduce((max, p) => Math.max(max, discountPercent(p.priceVnd, p.compareAtPriceVnd)), 0);

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0 lg:py-0">
      {/* Hero — thẻ xanh đặc ở mobile, thẻ trắng kèm thang pH (API) từ `md`. */}
      <section className="overflow-hidden rounded-2xl bg-gradient-to-br from-primary-dark to-primary shadow-brand-lg md:bg-surface md:bg-none">
        <div className="flex flex-col gap-6 p-5 md:p-6 lg:flex-row lg:items-center lg:gap-8 xl:p-8">
          <div className="min-w-0 flex-1">
            {bestDiscount > 0 ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1 text-[11px] font-bold text-secondary-text-on md:bg-danger-bg md:text-danger-text">
                <Tag size={11} aria-hidden="true" />
                {t("hero.savingBadge", { percent: bestDiscount })}
              </span>
            ) : null}
            <h1 className="pt-3 text-[24px] font-bold leading-tight text-white md:text-[28px] md:text-text-primary xl:text-[32px]">
              {t("hero.title")}
              <span className="block text-secondary md:text-primary">{t("hero.subtitle")}</span>
            </h1>
            <p className="max-w-[560px] pt-3 text-[13px] leading-relaxed text-on-primary-subtle md:text-[14px] md:text-text-secondary">
              {t("hero.body")}
            </p>
            {products.length > 0 ? (
              <div className="hidden flex-wrap gap-3 pt-4 md:flex">
                {lowestPrice !== null ? (
                  <span className="flex items-center gap-1.5 rounded-lg bg-background-alt px-2.5 py-1.5 text-[12px] font-semibold text-text-secondary">
                    <Tag size={12} aria-hidden="true" />
                    {t("hero.priceFrom", { price: formatVnd(lowestPrice) })}
                  </span>
                ) : null}
                <span className="flex items-center gap-1.5 rounded-lg bg-background-alt px-2.5 py-1.5 text-[12px] font-semibold text-text-secondary">
                  <Boxes size={12} aria-hidden="true" />
                  {t("hero.productCount", { count: products.length })}
                </span>
              </div>
            ) : null}
            <div className="flex flex-col gap-2.5 pt-5 md:flex-row md:gap-4">
              <a
                href={`#${CATALOG_ANCHOR}`}
                className="flex items-center justify-center gap-2 rounded-xl bg-surface px-5 py-3 text-[13px] font-bold text-primary-dark shadow-brand-md hover:bg-chip-bg md:bg-primary-dark md:text-white md:hover:bg-primary"
              >
                {t("hero.ctaPrimary")}
                <ArrowRight size={15} aria-hidden="true" />
              </a>
            </div>
          </div>

          <PhBandScale compact className="hidden shrink-0 bg-background-alt/50 shadow-none md:block lg:w-[340px]" />
        </div>
      </section>

      {/* Danh mục + giỏ hàng */}
      <div id={CATALOG_ANCHOR} className="flex scroll-mt-4 flex-col gap-5 lg:flex-row lg:items-start lg:gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <div className="relative -mx-4 md:mx-0">
            <div
              role="group"
              aria-label={t("filters.legend")}
              className="flex items-center gap-1.5 overflow-x-auto px-4 pr-10 md:flex-wrap md:overflow-x-visible md:rounded-2xl md:bg-surface md:p-3 md:shadow-brand-md"
            >
              {FILTERS.map((f) => (
                <button
                  key={f}
                  type="button"
                  aria-pressed={activeFilter === f}
                  onClick={() => {
                    setActiveFilter(f);
                  }}
                  className={cn(
                    "shrink-0 whitespace-nowrap rounded-full px-4 py-2 text-[12px] md:rounded-xl md:px-3.5 font-semibold transition-colors",
                    activeFilter === f
                      ? "bg-primary-dark text-white"
                      : "bg-surface text-text-secondary hover:bg-chip-bg md:bg-background-alt",
                  )}
                >
                  {t(`filters.${f}`)}
                </button>
              ))}
              <button
                type="button"
                onClick={() => {
                  setPriceAscending((asc) => !asc);
                }}
                className="ml-auto flex h-9 shrink-0 items-center gap-1.5 whitespace-nowrap rounded-full md:rounded-xl bg-surface px-3 text-[12px] font-semibold text-text-secondary hover:bg-chip-bg md:bg-background-alt"
              >
                <ArrowDownUp size={12} aria-hidden="true" />
                {priceAscending ? t("filters.sortPriceAsc") : t("filters.sortPriceDesc")}
              </button>
            </div>
            <span
              aria-hidden="true"
              className="pointer-events-none absolute inset-y-0 right-0 w-8 bg-gradient-to-l from-background to-transparent md:hidden"
            />
          </div>

          <CartShortcut />

          {productsQuery.isPending ? (
            <CatalogSkeleton />
          ) : productsQuery.isError ? (
            <ErrorState
              title={t("product.error")}
              onRetry={() => {
                void productsQuery.refetch();
              }}
              className="rounded-2xl bg-surface shadow-brand-md"
            />
          ) : products.length === 0 ? (
            <p className="rounded-2xl bg-surface p-6 text-center text-[13px] text-text-secondary shadow-brand-md">
              {t("product.empty")}
            </p>
          ) : visibleProducts.length === 0 ? (
            <p className="rounded-2xl bg-surface p-6 text-center text-[13px] text-text-secondary shadow-brand-md">
              {t("filters.empty")}
            </p>
          ) : (
            <>
              {/* Bản mobile: danh sách hàng ngang */}
              <div className="flex flex-col gap-3 md:hidden">
                {visibleProducts.map((p) => (
                  <MobileProductCard key={p.id} product={p} />
                ))}
              </div>

              {/* Bản web: lưới sản phẩm */}
              <div className="hidden gap-4 md:grid md:grid-cols-2 xl:grid-cols-3">
                {visibleProducts.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
            </>
          )}

          <CareNoteCard />
        </div>

        {/* Cột giỏ hàng chỉ từ `lg` (design web). Dưới `lg` dùng route `/cart` riêng. */}
        <div className="hidden lg:sticky lg:top-6 lg:block lg:w-[300px] lg:shrink-0 xl:w-[320px]">
          <CartPanel />
        </div>
      </div>

      <BuyingFacts />
    </div>
  );
}
