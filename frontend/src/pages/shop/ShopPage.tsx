import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  ArrowRight,
  BadgeCheck,
  FlaskConical,
  Gift,
  Heart,
  Package,
  Recycle,
  ScanLine,
  ShieldCheck,
  ShoppingCart,
  SlidersHorizontal,
  Smile,
  Sparkles,
  Star,
  Stethoscope,
  TestTubeDiagonal,
  Truck,
  Wind,
  Zap,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { CartPanel } from "./CartPanel";
import {
  MOCK_DELIVERY_COMMITMENTS,
  MOCK_MOBILE_PRODUCTS,
  MOCK_PRODUCTS,
  SHOP_HERO_IMAGE,
  formatVnd,
  type MockMobileProduct,
  type MockProduct,
} from "./mockData";
import { useShopCart } from "./useShopCart";

/**
 * `/shop` — Cửa hàng Cát Thông Minh.
 *
 * Hai bản thiết kế khác nhau được dựng trong cùng một trang:
 *   - từ `sm` trở lên: bản WEB `16:5849` (1280×1785) — hero ảnh, lưới 3 sản phẩm, cột giỏ
 *     hàng dính bên phải;
 *   - dưới `sm`: bản MOBILE `1:4066` (390×1956) — hero thẻ xanh đặc, danh sách 5 gói dạng
 *     hàng ngang, khối "Cam kết giao hàng CATCHECK".
 * Hai bản dùng hai catalogue khác nhau (`MOCK_PRODUCTS` / `MOCK_MOBILE_PRODUCTS`) đúng như
 * thiết kế. Toàn bộ dữ liệu là mock — xem `mockData.ts`.
 *
 * Khung app (sidebar trái + thanh tìm kiếm trên) thuộc `AppLayout`, KHÔNG dựng ở đây.
 */

const FILTERS = ["all", "trial", "subscription"] as const;

const COMMITMENTS = [
  { key: "dust", icon: Wind, tone: "bg-info text-primary-dark" },
  { key: "nontoxic", icon: Smile, tone: "bg-secondary/25 text-secondary-text-on" },
  { key: "isfm", icon: ShieldCheck, tone: "bg-success-bg text-success-text" },
  { key: "biodegradable", icon: Recycle, tone: "bg-chip-bg text-primary-dark" },
] as const;

const EXTRA_NOTE_ICONS = { gift: Gift, phRange: TestTubeDiagonal, appScan: ScanLine } as const;

const DELIVERY_COMMITMENT_TONES: Record<(typeof MOCK_DELIVERY_COMMITMENTS)[number]["key"], string> = {
  fast: "bg-chip-bg text-primary",
  genuine: "bg-secondary/35 text-secondary-text-on",
  clinic: "bg-success-bg text-success-text",
};

const DELIVERY_COMMITMENT_ICONS: Record<(typeof MOCK_DELIVERY_COMMITMENTS)[number]["key"], typeof Zap> = {
  fast: Zap,
  genuine: Package,
  clinic: ShieldCheck,
};

/** Thẻ sản phẩm bản mobile: ảnh vuông bên trái, nút "Thêm vào giỏ" chiếm nửa hàng dưới. */
function MobileProductCard({ product }: { product: MockMobileProduct }) {
  const { t } = useTranslation("shop");
  const add = useShopCart((s) => s.add);

  return (
    <article className="rounded-2xl bg-surface p-3 shadow-brand-md">
      <div className="flex gap-3">
        <div className="relative size-[88px] shrink-0 overflow-hidden rounded-xl">
          <img src={product.imageUrl} alt="" className="size-full object-cover" />
          <span className="absolute left-1 top-1 rounded-full bg-surface/95 px-2 py-0.5 text-[10px] font-bold text-primary-dark shadow-xs">
            {product.weightBadge}
          </span>
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-start justify-between gap-2">
            <span
              className={cn(
                "inline-flex rounded-full px-2.5 py-0.5 text-[11px] font-bold",
                product.tagTone === "secondary"
                  ? "bg-secondary/35 text-secondary-text-on"
                  : "bg-chip-bg text-primary-dark",
              )}
            >
              {product.tag}
            </span>
            <button
              type="button"
              aria-label={t("product.saveForLater")}
              className="shrink-0 text-text-tertiary hover:text-danger"
            >
              <Heart size={18} aria-hidden="true" />
            </button>
          </div>
          <Link
            to={`/shop/products/${product.id}`}
            className="block pt-1 text-[15px] font-bold leading-snug text-text-primary hover:text-primary-dark"
          >
            {product.name}
          </Link>
          <p className="text-[12px] text-text-tertiary">{product.subtitle}</p>
          <p className="line-clamp-2 pt-0.5 text-[12px] leading-relaxed text-text-secondary">
            {product.description}
          </p>
        </div>
      </div>

      <div className="flex items-end justify-between gap-3 pt-3">
        <span className="min-w-0">
          <span
            className={cn(
              "block text-[12px]",
              product.priceLabelIsSaving ? "font-semibold text-success-text" : "text-text-tertiary",
            )}
          >
            {product.priceLabel}
          </span>
          <span className="block text-[20px] font-bold leading-tight text-primary-dark">
            {formatVnd(product.price)}
          </span>
        </span>
        <button
          type="button"
          onClick={() => {
            add({
              id: `line-${product.id}`,
              name: product.name,
              subtitle: product.subtitle,
              imageUrl: product.imageUrl,
              unitPrice: product.price,
              quantity: 1,
            });
          }}
          className="flex shrink-0 items-center gap-2 rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white shadow-brand-md hover:bg-primary"
        >
          <ShoppingCart size={15} aria-hidden="true" />
          {t("product.addToCartLong")}
        </button>
      </div>
    </article>
  );
}

function ProductCard({ product }: { product: MockProduct }) {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const add = useShopCart((s) => s.add);

  const toneClass =
    product.cornerBadgeTone === "secondary"
      ? "bg-surface text-secondary-text-on"
      : product.cornerBadgeTone === "success"
        ? "bg-success text-white"
        : "bg-surface text-primary-dark";

  return (
    <article className="flex flex-col rounded-2xl bg-surface p-3 shadow-brand-md">
      <div className="relative overflow-hidden rounded-xl">
        <img src={product.imageUrl} alt="" className="aspect-[4/3] w-full object-cover" />
        <span
          className={cn(
            "absolute left-2 top-2 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-bold shadow-xs",
            toneClass,
          )}
        >
          {product.cornerBadgeTone === "secondary" ? <Star size={10} aria-hidden="true" /> : null}
          {product.cornerBadge}
        </span>
        <span className="absolute bottom-2 left-1/2 -translate-x-1/2 rounded-full bg-surface/95 px-2.5 py-0.5 text-[10px] font-semibold text-text-primary shadow-xs">
          {product.imageBadge}
        </span>
      </div>

      <p className="flex items-center gap-1 pt-2.5 text-[11px] text-text-secondary">
        <Star size={11} className="text-secondary" fill="currentColor" aria-hidden="true" />
        <span className="font-bold text-text-primary">{product.rating.toFixed(1)}</span>
        {t("product.reviewCount", { count: product.ratingCount.toLocaleString("vi-VN") })}
      </p>

      <Link
        to={`/shop/products/${product.id}`}
        className="pt-1 text-[15px] font-bold leading-snug text-text-primary hover:text-primary-dark"
      >
        {product.name}
      </Link>
      <p className="pt-1 text-[12px] leading-relaxed text-text-secondary">{product.description}</p>

      {product.extraNote ? (
        (() => {
          const kind = product.extraNoteKind ?? "gift";
          const NoteIcon = EXTRA_NOTE_ICONS[kind];
          const isPhRange = kind === "phRange";
          return (
            <div className="pt-2">
              <p
                className={cn(
                  "flex items-center gap-1.5 rounded-lg px-2 py-1.5 text-[11px] font-semibold",
                  isPhRange
                    ? "bg-chip-bg/70 text-text-secondary"
                    : kind === "appScan"
                      ? "bg-chip-bg/70 text-primary-dark"
                      : "bg-secondary/15 text-secondary-text-on",
                )}
              >
                <NoteIcon size={12} className="shrink-0" aria-hidden="true" />
                {product.extraNote}
                {product.extraNoteValue ? (
                  <span className="ml-auto text-right font-bold text-primary-dark">
                    {product.extraNoteValue}
                  </span>
                ) : null}
              </p>
              {isPhRange ? (
                <span
                  aria-hidden="true"
                  className="mt-1 block h-1.5 w-full rounded-full bg-gradient-to-r from-warning via-success to-primary-dark"
                />
              ) : null}
            </div>
          );
        })()
      ) : null}

      <div className="flex items-baseline gap-1.5 pt-3">
        <span className="text-[18px] font-bold text-primary-dark">{formatVnd(product.price)}</span>
        <s className="text-[12px] text-text-tertiary">{formatVnd(product.compareAtPrice)}</s>
        <span className="rounded bg-danger-bg px-1 py-0.5 text-[10px] font-bold text-danger-text">
          -{product.discountPercent}%
        </span>
      </div>

      <div className="flex gap-2 pt-3">
        <button
          type="button"
          onClick={() => {
            add({
              id: `line-${product.id}`,
              name: product.name,
              subtitle: product.imageBadge,
              imageUrl: product.imageUrl,
              unitPrice: product.price,
              quantity: 1,
            });
          }}
          className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-background-alt px-3 py-2 text-[12px] font-semibold text-text-primary hover:bg-chip-bg"
        >
          <ShoppingCart size={13} aria-hidden="true" />
          {t("product.addToCart")}
        </button>
        <button
          type="button"
          onClick={() => { void navigate(`/shop/products/${product.id}`); }}
          className="flex-1 rounded-xl bg-primary-dark px-3 py-2 text-[12px] font-semibold text-white hover:bg-primary"
        >
          {product.secondaryCta === "subscribe" ? t("product.subscribe") : t("product.buyNow")}
        </button>
      </div>
    </article>
  );
}

export function ShopPage() {
  const { t } = useTranslation("shop");
  const [activeFilter, setActiveFilter] = useState<(typeof FILTERS)[number]>("all");

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0">
      {/* Thanh chính sách (Figma: dải vàng nhạt trên cùng) */}
      <div className="hidden flex-col gap-1 rounded-xl bg-secondary/15 px-4 py-2.5 text-[12px] text-secondary-text-on sm:flex lg:flex-row lg:items-center lg:justify-between">
        <span className="flex items-center gap-2 font-semibold">
          <BadgeCheck size={14} className="shrink-0" aria-hidden="true" />
          {t("policyBar.text")}
        </span>
        <span className="shrink-0 pl-6 lg:pl-0">• {t("policyBar.stock")}</span>
      </div>

      {/* Hero */}
      {/* Hero — thẻ xanh đặc ở mobile (`1:4066`), thẻ trắng kèm ảnh từ `sm` (`16:5849`) */}
      <section className="overflow-hidden rounded-2xl bg-primary-dark shadow-brand-lg sm:bg-surface">
        <div className="flex flex-col gap-6 p-5 sm:p-6 lg:flex-row lg:items-center lg:gap-8">
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-surface/20 px-3 py-1 text-[10px] font-bold tracking-[0.5px] text-white sm:bg-chip-bg sm:text-primary-dark">
                {t("hero.badge")}
              </span>
              <span className="flex items-center gap-1 rounded-full bg-secondary px-2.5 py-1 text-[10px] font-bold text-secondary-text-on sm:bg-danger-bg sm:text-danger-text">
                <Sparkles size={11} aria-hidden="true" />
                {t("hero.savingBadge")}
              </span>
            </div>
            <h1 className="pt-3 text-[26px] font-bold leading-tight text-white sm:text-text-primary lg:text-[32px]">
              {t("hero.titleLine1")}
              <span className="block text-secondary sm:text-primary">{t("hero.titleLine2")}</span>
            </h1>
            <p className="max-w-[520px] pt-3 text-[13px] leading-relaxed text-on-primary-subtle sm:text-text-secondary">
              {t("hero.body")}
            </p>
            <div className="hidden flex-wrap gap-2 pt-4 sm:flex">
              <span className="flex items-center gap-1.5 rounded-lg bg-background-alt px-2.5 py-1.5 text-[11px] font-semibold text-text-secondary">
                <Gift size={12} aria-hidden="true" />
                {t("hero.perkSpoon")}
              </span>
              <span className="flex items-center gap-1.5 rounded-lg bg-background-alt px-2.5 py-1.5 text-[11px] font-semibold text-text-secondary">
                <Truck size={12} aria-hidden="true" />
                {t("hero.perkFreeship")}
              </span>
            </div>
            <div className="flex flex-col gap-2.5 pt-5 sm:flex-row">
              <button
                type="button"
                className="flex items-center justify-center gap-2 rounded-xl bg-surface px-5 py-3 text-[13px] font-bold text-primary-dark shadow-brand-md hover:bg-chip-bg sm:bg-primary sm:text-white sm:hover:bg-primary-dark"
              >
                {t("hero.ctaPrimary")}
                <ArrowRight size={15} aria-hidden="true" />
              </button>
              <button
                type="button"
                className="flex items-center justify-center gap-2 rounded-xl bg-surface/20 px-5 py-3 text-[13px] font-bold text-white hover:bg-surface/30 sm:bg-chip-bg sm:text-primary-dark sm:hover:bg-info"
              >
                <FlaskConical size={15} aria-hidden="true" />
                {t("hero.ctaSecondary")}
              </button>
            </div>
          </div>

          <div className="relative hidden shrink-0 overflow-hidden rounded-2xl sm:block lg:w-[300px]">
            <img src={SHOP_HERO_IMAGE} alt="" className="aspect-[300/220] w-full object-cover" />
            <div className="absolute inset-x-2 bottom-2 flex items-center justify-between rounded-xl bg-surface/95 px-3 py-1.5 backdrop-blur-sm">
              <span className="flex items-center gap-1.5 text-[10px] font-semibold text-text-primary">
                <span className="flex gap-0.5" aria-hidden="true">
                  <i className="size-1.5 rounded-full bg-secondary" />
                  <i className="size-1.5 rounded-full bg-success" />
                  <i className="size-1.5 rounded-full bg-primary" />
                </span>
                {t("hero.imageCaption")}
              </span>
              <span className="text-[10px] font-bold text-primary-dark">{t("hero.imageAccuracy")}</span>
            </div>
          </div>
        </div>
      </section>

      {/* Danh mục + giỏ hàng */}
      <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
        <div className="min-w-0 flex-1">
          <div className="-mx-4 flex items-center gap-2 overflow-x-auto px-4 pb-4 sm:mx-0 sm:flex-wrap sm:overflow-x-visible sm:px-0">
            {FILTERS.map((f) => (
              <button
                key={f}
                type="button"
                onClick={() => { setActiveFilter(f); }}
                className={cn(
                  "shrink-0 rounded-xl px-3.5 py-2 text-[12px] font-semibold transition-colors",
                  activeFilter === f
                    ? "bg-primary text-white"
                    : "bg-surface text-text-secondary hover:bg-background-alt",
                )}
              >
                {t(`filters.${f}`)}
              </button>
            ))}
            <span className="ml-auto hidden gap-2 sm:flex">
              <button
                type="button"
                className="flex items-center gap-1.5 rounded-xl bg-surface px-3 py-2 text-[11px] font-semibold text-text-secondary hover:bg-background-alt"
              >
                <SlidersHorizontal size={12} aria-hidden="true" />
                {t("filters.byFeature")}
              </button>
              <button
                type="button"
                className="flex items-center gap-1.5 rounded-xl bg-surface px-3 py-2 text-[11px] font-semibold text-text-secondary hover:bg-background-alt"
              >
                <ArrowRight size={12} className="-rotate-90" aria-hidden="true" />
                {t("filters.byPrice")}
              </button>
            </span>
          </div>

          {/* Bản mobile: 5 gói dạng hàng ngang (`1:4066`) */}
          <div className="flex flex-col gap-3 sm:hidden">
            {MOCK_MOBILE_PRODUCTS.map((p) => (
              <MobileProductCard key={p.id} product={p} />
            ))}
          </div>

          {/* Bản web: lưới 3 sản phẩm (`16:5849`) */}
          <div className="hidden gap-4 sm:grid sm:grid-cols-2 xl:grid-cols-3">
            {MOCK_PRODUCTS.map((p) => (
              <ProductCard key={p.id} product={p} />
            ))}
          </div>

          {/* Dải tư vấn bác sĩ */}
          <div className="mt-5 flex flex-col gap-3 rounded-2xl bg-chip-bg/60 p-4 lg:flex-row lg:items-center">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary text-white">
              <Stethoscope size={18} aria-hidden="true" />
            </span>
            <div className="min-w-0 flex-1">
              <p className="text-[13px] font-bold text-text-primary">{t("advisor.title")}</p>
              <p className="text-[12px] text-text-secondary">{t("advisor.body")}</p>
            </div>
            <button
              type="button"
              className="shrink-0 rounded-xl bg-surface px-4 py-2 text-[12px] font-semibold text-primary-dark shadow-xs hover:bg-background-alt"
            >
              {t("advisor.cta")}
            </button>
          </div>
        </div>

        <div className="w-full lg:sticky lg:top-4 lg:w-[320px] lg:shrink-0">
          <CartPanel />
        </div>
      </div>

      {/* Cam kết giao hàng — chỉ bản mobile (`1:4066`) */}
      <section className="rounded-2xl bg-chip-bg/60 p-4 sm:hidden">
        <h2 className="flex items-center gap-2 text-[17px] font-bold text-text-primary">
          <ShieldCheck size={18} className="text-primary-dark" aria-hidden="true" />
          {t("deliveryCommitment.title")}
        </h2>
        <ul className="flex flex-col gap-2.5 pt-3">
          {MOCK_DELIVERY_COMMITMENTS.map((item) => {
            const Icon = DELIVERY_COMMITMENT_ICONS[item.key];
            return (
              <li key={item.key} className="flex items-center gap-3 rounded-xl bg-surface p-3">
                <span
                  className={cn(
                    "flex size-10 shrink-0 items-center justify-center rounded-full",
                    DELIVERY_COMMITMENT_TONES[item.key],
                  )}
                >
                  <Icon size={18} aria-hidden="true" />
                </span>
                <span className="min-w-0">
                  <span className="block text-[13px] font-bold text-text-primary">{item.title}</span>
                  <span className="block pt-0.5 text-[12px] leading-relaxed text-text-secondary">{item.body}</span>
                </span>
              </li>
            );
          })}
        </ul>
      </section>

      {/* Cam kết chất lượng — chỉ bản web (`16:5849`) */}
      <section className="hidden rounded-2xl bg-surface p-6 shadow-brand-md sm:block">
        <div className="text-center">
          <span className="rounded-full bg-success-bg px-3 py-1 text-[10px] font-bold tracking-[0.5px] text-success-text">
            {t("commitment.eyebrow")}
          </span>
          <h2 className="pt-3 text-[22px] font-bold text-text-primary">{t("commitment.title")}</h2>
          <p className="mx-auto max-w-[620px] pt-2 text-[12px] text-text-secondary">{t("commitment.subtitle")}</p>
        </div>
        <div className="grid gap-4 pt-6 sm:grid-cols-2 xl:grid-cols-4">
          {COMMITMENTS.map(({ key, icon: Icon, tone }) => (
            <div key={key} className="rounded-xl bg-background-alt/60 p-4">
              <span className={cn("mb-3 flex size-10 items-center justify-center rounded-xl", tone)}>
                <Icon size={18} aria-hidden="true" />
              </span>
              <p className="text-[14px] font-bold leading-snug text-text-primary">
                {t(`commitment.items.${key}.title`)}
              </p>
              <p className="pt-1.5 text-[12px] leading-relaxed text-text-secondary">
                {t(`commitment.items.${key}.body`)}
              </p>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}
