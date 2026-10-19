import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import { Skeleton as AntSkeleton } from "antd";
import { ArrowDownUp, ArrowRight, Boxes, ClipboardList, Loader2, ShoppingCart, Tag } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { useListReveal } from "@/shared/lib/motion/usePageReveal";
import { ErrorState } from "@/shared/ui";
import { useShopProducts, type ShopProductApi } from "@/features/shop";
import { CartPanel } from "./CartPanel";
import { discountPercent, formatVnd } from "./shopFormat";
import { BuyingFacts, CareNoteCard, CartShortcut, PhBandScale, PriceRow, ProductGlyph, StockChip } from "./shopUi";
import { useAddToCart } from "./useCartActions";


const FILTERS = ["all", "subscription", "single"] as const;
type FilterKey = (typeof FILTERS)[number];

const CATALOG_ANCHOR = "shop-catalog";

function isSubscription(sku: string): boolean {
  return sku.startsWith("SUBSCRIPTION");
}

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

function MobileProductCard({ product }: { product: ShopProductApi }) {
  const { t } = useTranslation("shop");
  const outOfStock = product.stockQuantity <= 0;

  return (
    <article data-reveal-item className="motion-card rounded-lg bg-surface p-2.5 shadow-brand-sm">
      <div className="flex gap-2.5">
        <Link to={`/shop/products/${product.id}`} className="shrink-0" tabIndex={-1} aria-hidden="true">
          <ProductGlyph sku={product.sku} imageUrl={product.imageUrl} className="size-[80px]" iconSize={28} />
        </Link>
        <div className="min-w-0 flex-1">
          <span className="inline-flex max-w-full truncate rounded-full bg-chip-bg px-2 py-0.5 text-[10px] font-bold text-primary-dark">
            {t("product.skuLabel", { sku: product.sku })}
          </span>
          <Link
            to={`/shop/products/${product.id}`}
            className="block pt-0.5 text-[14px] font-bold leading-snug text-text-primary hover:text-primary-dark"
          >
            {product.name}
          </Link>
          <p className="line-clamp-2 pt-0.5 text-[11px] leading-relaxed text-text-secondary">{product.description}</p>
          <div className="pt-1">
            <StockChip stockQuantity={product.stockQuantity} />
          </div>
        </div>
      </div>

      <div className="flex items-end justify-between gap-2.5 pt-2.5">
        <PriceRow product={product} className="min-w-0" />
        <AddToCartButton
          product={product}
          iconSize={13}
          label={outOfStock ? t("product.outOfStock") : t("product.addToCartLong")}
          className="flex shrink-0 items-center gap-1.5 rounded-md bg-primary-dark px-3 py-2.5 text-[12px] font-bold text-white shadow-brand-sm hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
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
    <article data-reveal-item className="motion-card flex flex-col rounded-lg bg-surface p-2.5 shadow-brand-sm">
      <Link to={`/shop/products/${product.id}`} tabIndex={-1} aria-hidden="true">
        <ProductGlyph
          sku={product.sku}
          imageUrl={product.imageUrl}
          className="aspect-[164/150] w-full"
          iconSize={40}
          showSku
        />
      </Link>

      <Link
        to={`/shop/products/${product.id}`}
        className="pt-2.5 text-[14px] font-bold leading-snug text-text-primary hover:text-primary-dark"
      >
        {product.name}
      </Link>
      <p className="line-clamp-2 pt-0.5 text-[11px] leading-relaxed text-text-secondary">{product.description}</p>
      <div className="pt-1.5">
        <StockChip stockQuantity={product.stockQuantity} />
      </div>

      <PriceRow product={product} className="mt-auto pt-2.5" />

      <div className="flex gap-1 pt-2.5">
        <AddToCartButton
          product={product}
          iconSize={12}
          label={t("product.addToCart")}
          className="flex flex-1 items-center justify-center gap-0.5 whitespace-nowrap rounded-md bg-chip-bg px-1 py-2 text-[11px] font-semibold text-primary-dark hover:bg-info disabled:cursor-not-allowed disabled:opacity-50"
        />
        <button
          type="button"
          disabled={outOfStock || pending}
          onClick={() => {
            void add(product, 1, { goToCheckout: true });
          }}
          className="flex-1 whitespace-nowrap rounded-md bg-primary-dark px-1 py-2 text-[11px] font-semibold text-white hover:bg-primary disabled:cursor-not-allowed disabled:opacity-50"
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
      <div className="flex flex-col gap-2.5 md:hidden" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <div key={i} className="rounded-lg bg-surface p-3" aria-hidden="true">
            <AntSkeleton active avatar paragraph={{ rows: 2 }} />
          </div>
        ))}
      </div>
      <div className="hidden gap-3 md:grid md:grid-cols-2 xl:grid-cols-3" aria-hidden="true">
        {[0, 1, 2].map((i) => (
          <div key={i} className="rounded-lg bg-surface p-3" aria-hidden="true">
            <AntSkeleton active avatar paragraph={{ rows: 5 }} />
          </div>
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

  const mobileListRef = useRef<HTMLDivElement>(null);
  const gridRef = useRef<HTMLDivElement>(null);
  const listKey = `${activeFilter}:${String(priceAscending)}:${String(visibleProducts.length)}`;
  useListReveal(mobileListRef, listKey);
  useListReveal(gridRef, listKey);

  const lowestPrice = products.length > 0 ? Math.min(...products.map((p) => p.priceVnd)) : null;
  const bestDiscount = products.reduce((max, p) => Math.max(max, discountPercent(p.priceVnd, p.compareAtPriceVnd)), 0);

  return (
    <div className="flex flex-col gap-4 px-4 py-4 lg:px-0 lg:py-0">
      <section data-reveal className="overflow-hidden rounded-lg bg-gradient-to-br from-primary-dark to-primary shadow-brand-sm md:bg-surface md:bg-none">
        <div className="flex flex-col gap-4 p-3 md:p-4 lg:flex-row lg:items-center lg:gap-6 xl:p-5">
          <div className="min-w-0 flex-1">
            {bestDiscount > 0 ? (
              <span className="inline-flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1 text-[11px] font-bold text-secondary-text-on md:bg-danger-bg md:text-danger-text">
                <Tag size={11} aria-hidden="true" />
                {t("hero.savingBadge", { percent: bestDiscount })}
              </span>
            ) : null}
            <h1 className="pt-2 text-[20px] font-bold leading-tight text-white md:text-[22px] md:text-text-primary xl:text-[24px]">
              {t("hero.title")}
              <span className="block text-secondary md:text-primary">{t("hero.subtitle")}</span>
            </h1>
            <p className="max-w-[560px] pt-2 text-[12px] leading-relaxed text-on-primary-subtle md:text-[13px] md:text-text-secondary">
              {t("hero.body")}
            </p>
            {products.length > 0 ? (
              <div className="hidden flex-wrap gap-2 pt-2 md:flex">
                {lowestPrice !== null ? (
                  <span className="flex items-center gap-1.5 rounded-sm bg-background-alt px-2 py-1 text-[11px] font-semibold text-text-secondary">
                    <Tag size={11} aria-hidden="true" />
                    {t("hero.priceFrom", { price: formatVnd(lowestPrice) })}
                  </span>
                ) : null}
                <span className="flex items-center gap-1.5 rounded-sm bg-background-alt px-2 py-1 text-[11px] font-semibold text-text-secondary">
                  <Boxes size={11} aria-hidden="true" />
                  {t("hero.productCount", { count: products.length })}
                </span>
              </div>
            ) : null}
            <div className="flex flex-col gap-2 pt-3 md:flex-row md:gap-3">
              <a
                href={`#${CATALOG_ANCHOR}`}
                className="flex items-center justify-center gap-2 rounded-lg bg-surface px-4 py-2.5 text-[12px] font-bold text-primary-dark shadow-brand-sm hover:bg-chip-bg md:bg-primary-dark md:text-white md:hover:bg-primary"
              >
                {t("hero.ctaPrimary")}
                <ArrowRight size={14} aria-hidden="true" />
              </a>
              <Link
                to="/orders"
                className="flex items-center justify-center gap-2 rounded-lg bg-chip-bg px-4 py-2.5 text-[12px] font-bold text-primary-dark hover:bg-info"
              >
                <ClipboardList size={14} aria-hidden="true" />
                {t("myOrders.link")}
              </Link>
            </div>
          </div>

          <PhBandScale compact className="hidden shrink-0 bg-background-alt/50 shadow-none md:block lg:w-[340px]" />
        </div>
      </section>

      <div data-reveal id={CATALOG_ANCHOR} className="flex scroll-mt-4 flex-col gap-4 lg:flex-row lg:items-start lg:gap-5">
        <div className="flex min-w-0 flex-1 flex-col gap-3">
          <div className="relative -mx-4 md:mx-0">
            <div
              role="group"
              aria-label={t("filters.legend")}
              className="flex items-center gap-1 overflow-x-auto px-4 pr-10 md:flex-wrap md:overflow-x-visible md:rounded-lg md:bg-surface md:p-2.5 md:shadow-brand-sm"
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
                    "shrink-0 whitespace-nowrap rounded-full px-3 py-1.5 text-[11px] md:rounded-lg md:px-3 font-semibold transition-colors",
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
                className="ml-auto flex h-8 shrink-0 items-center gap-1 whitespace-nowrap rounded-full md:rounded-md bg-surface px-2.5 text-[11px] font-semibold text-text-secondary hover:bg-chip-bg md:bg-background-alt"
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
              className="rounded-lg bg-surface shadow-brand-sm"
            />
          ) : products.length === 0 ? (
            <p className="rounded-lg bg-surface p-4 text-center text-[12px] text-text-secondary shadow-brand-sm">
              {t("product.empty")}
            </p>
          ) : visibleProducts.length === 0 ? (
            <p className="rounded-lg bg-surface p-4 text-center text-[12px] text-text-secondary shadow-brand-sm">
              {t("filters.empty")}
            </p>
          ) : (
            <>
              <div ref={mobileListRef} className="flex flex-col gap-2.5 md:hidden">
                {visibleProducts.map((p) => (
                  <MobileProductCard key={p.id} product={p} />
                ))}
              </div>

              <div ref={gridRef} className="hidden gap-3 md:grid md:grid-cols-2 xl:grid-cols-3">
                {visibleProducts.map((p) => (
                  <ProductCard key={p.id} product={p} />
                ))}
              </div>
            </>
          )}

          <CareNoteCard />
        </div>

        <div className="hidden lg:sticky lg:top-6 lg:block lg:w-[300px] lg:shrink-0 xl:w-[320px]">
          <CartPanel />
        </div>
      </div>

      <div data-reveal>
        <BuyingFacts />
      </div>
    </div>
  );
}
