import { useTranslation } from "react-i18next";
import { Link } from "react-router";
import {
  Box,
  Boxes,
  CalendarSync,
  ChevronRight,
  CreditCard,
  Droplet,
  FlaskConical,
  Info,
  Loader2,
  Minus,
  Package,
  PackageX,
  Plus,
  ScanLine,
  ShoppingCart,
  Truck,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { PhDisclaimerNote, PhGaugeBar, PhRangeLegend, phTokenStyle, usePhBands } from "@/entities/ph-bands";
import { useShopCart, type ShopProductApi } from "@/features/shop";
import { formatPh } from "@/shared/lib/format/formatPh";
import { discountPercent, formatVnd } from "./shopFormat";


function glyphForSku(sku: string): LucideIcon {
  if (sku.startsWith("SUBSCRIPTION")) return CalendarSync;
  if (sku.startsWith("CLEANBOX")) return Box;
  if (sku.startsWith("SMARTSAND")) return FlaskConical;
  return Package;
}

export function ProductGlyph({
  sku,
  imageUrl,
  className,
  iconSize = 28,
  showSku = false,
}: {
  sku: string;
  imageUrl?: string | null;
  className?: string;
  iconSize?: number;
  showSku?: boolean;
}) {
  const Icon = glyphForSku(sku);
  return (
    <span
      className={cn(
        "relative flex items-center justify-center overflow-hidden rounded-lg bg-gradient-to-br from-chip-bg to-background-alt",
        className,
      )}
    >
      {imageUrl ? (
        <img src={imageUrl} alt="" className="size-full object-cover" loading="lazy" />
      ) : (
        <Icon size={iconSize} className="text-primary-dark/70" aria-hidden="true" />
      )}
      {showSku ? (
        <span className="absolute bottom-1.5 left-1.5 right-1.5 truncate rounded-full bg-surface/90 px-1.5 py-0.5 text-center text-[9px] font-bold text-primary-dark">
          {sku}
        </span>
      ) : null}
    </span>
  );
}

export function StockChip({ stockQuantity }: { stockQuantity: number }) {
  const { t } = useTranslation("shop");
  const inStock = stockQuantity > 0;
  return (
    <span
      className={cn(
        "inline-flex w-fit items-center gap-1.5 rounded-full px-2.5 py-1 text-[11px] font-semibold",
        inStock ? "bg-success-bg text-success-text" : "bg-background-alt text-text-tertiary",
      )}
    >
      {inStock ? <Boxes size={12} aria-hidden="true" /> : <PackageX size={12} aria-hidden="true" />}
      {inStock ? t("product.inStock", { count: stockQuantity }) : t("product.outOfStock")}
    </span>
  );
}

export function PriceRow({ product, className }: { product: ShopProductApi; className?: string }) {
  const { t } = useTranslation("shop");
  const percent = discountPercent(product.priceVnd, product.compareAtPriceVnd);
  return (
    <span className={cn("flex flex-wrap items-baseline gap-1.5", className)}>
      <span className="text-[18px] font-bold text-primary-dark">{formatVnd(product.priceVnd)}</span>
      {product.compareAtPriceVnd && product.compareAtPriceVnd > product.priceVnd ? (
        <s className="text-[12px] text-text-tertiary">{formatVnd(product.compareAtPriceVnd)}</s>
      ) : null}
      {percent > 0 ? (
        <span className="rounded bg-danger-bg px-1 py-0.5 text-[10px] font-bold text-danger-text">
          {t("product.discount", { percent })}
        </span>
      ) : null}
    </span>
  );
}

export function QuantityStepper({
  value,
  max,
  onChange,
  pending = false,
  size = "md",
  className,
}: {
  value: number;
  max: number;
  onChange: (next: number) => void;
  pending?: boolean;
  size?: "sm" | "md";
  className?: string;
}) {
  const { t } = useTranslation("shop");
  const button =
    size === "sm"
      ? "flex size-6 items-center justify-center rounded-md text-text-secondary hover:bg-chip-bg disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:bg-transparent"
      : "flex size-8 items-center justify-center rounded-lg text-text-secondary hover:bg-background-alt disabled:cursor-not-allowed disabled:opacity-35 disabled:hover:bg-transparent";
  const iconSize = size === "sm" ? 12 : 14;
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center gap-0.5 rounded-xl border border-border bg-surface",
        size === "sm" ? "p-0.5" : "p-1",
        className,
      )}
      aria-busy={pending || undefined}
    >
      <button
        type="button"
        aria-label={t("cartPanel.decrease")}
        disabled={pending || value <= 1}
        onClick={() => {
          onChange(value - 1);
        }}
        className={button}
      >
        <Minus size={iconSize} aria-hidden="true" />
      </button>
      <span
        className={cn(
          "flex items-center justify-center text-center font-bold text-text-primary",
          size === "sm" ? "min-w-5 text-[12px]" : "min-w-7 text-[14px]",
        )}
        aria-live="polite"
      >
        {pending ? <Loader2 size={iconSize} className="animate-spin text-text-tertiary" aria-hidden="true" /> : value}
      </span>
      <button
        type="button"
        aria-label={t("cartPanel.increase")}
        disabled={pending || value >= max}
        onClick={() => {
          onChange(value + 1);
        }}
        className={button}
      >
        <Plus size={iconSize} aria-hidden="true" />
      </button>
    </span>
  );
}

export function CartShortcut({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  const cartQuery = useShopCart();
  const cart = cartQuery.data;
  if (!cart || cart.lines.length === 0) return null;
  return (
    <Link
      to="/cart"
      className={cn(
        "flex items-center gap-2.5 rounded-lg bg-surface p-2.5 shadow-brand-sm hover:bg-background-alt lg:hidden",
        className,
      )}
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-chip-bg text-primary-dark">
        <ShoppingCart size={16} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[12px] font-bold text-text-primary">
          {t("cartShortcut.title", { count: cart.lines.length })}
        </span>
        <span className="block text-[11px] text-text-secondary">
          {t("cartShortcut.total", { total: formatVnd(cart.totalVnd) })}
        </span>
      </span>
      <span className="flex shrink-0 items-center gap-0.5 text-[11px] font-semibold text-primary-dark">
        {t("cartShortcut.cta")}
        <ChevronRight size={13} aria-hidden="true" />
      </span>
    </Link>
  );
}

export function ShippingFee({ value }: { value: number }) {
  const { t } = useTranslation("shop");
  return value === 0 ? (
    <span className="text-success-text">{t("cartPanel.freeShipping")}</span>
  ) : (
    <>{formatVnd(value)}</>
  );
}

function useBandRangeLabel() {
  const { t } = useTranslation("shop");
  return (min: number | null | undefined, max: number | null | undefined): string => {
    if (typeof min === "number" && typeof max === "number")
      return t("phScale.rangeBetween", { min: formatPh(min), max: formatPh(max) });
    if (typeof max === "number") return t("phScale.rangeBelow", { max: formatPh(max) });
    if (typeof min === "number") return t("phScale.rangeAbove", { min: formatPh(min) });
    return "";
  };
}

export function PhBandScale({ compact = false, className }: { compact?: boolean; className?: string }) {
  const { t } = useTranslation("shop");
  const bandsQuery = usePhBands();
  const rangeLabel = useBandRangeLabel();
  const bands = (bandsQuery.data ?? [])
    .filter((band) => typeof band.phMin === "number" || typeof band.phMax === "number")
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <section className={cn("rounded-lg bg-surface p-3.5 shadow-brand-sm", className)}>
      <h2 className="flex items-center gap-1.5 text-[15px] font-bold text-text-primary">
        <Droplet size={15} className="shrink-0 text-primary-dark" aria-hidden="true" />
        {t("phScale.title")}
      </h2>
      {!compact ? <p className="pt-1 text-[11px] leading-relaxed text-text-secondary">{t("phScale.body")}</p> : null}

      {bandsQuery.isPending ? (
        <p className="pt-2.5 text-[11px] text-text-tertiary">{t("phScale.loading")}</p>
      ) : bands.length === 0 ? (
        <p className="pt-2.5 text-[11px] text-text-tertiary">{t("phScale.error")}</p>
      ) : (
        <>
          <PhGaugeBar bands={bands} className="pt-2.5" />
          {compact ? (
            <PhRangeLegend bands={bands} className="pt-2" />
          ) : (
            <ul className="grid gap-1.5 pt-2.5 md:grid-cols-2">
              {bands.map((band) => {
                const style = phTokenStyle(band.colorToken);
                return (
                  <li key={band.code} className="flex gap-2 rounded-lg bg-background-alt/60 p-2.5">
                    <span className={cn("mt-0.5 size-2 shrink-0 rounded-full", style.solid)} aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="block text-[12px] font-bold leading-snug text-text-primary">{band.label}</span>
                      <span
                        className={cn(
                          "mt-0.5 inline-flex rounded-full px-1.5 py-0.5 text-[10px] font-semibold",
                          style.bg,
                          style.text,
                        )}
                      >
                        {rangeLabel(band.phMin, band.phMax)}
                      </span>
                      {band.description.includes("{0}") ? null : (
                        <span className="block pt-1 text-[10px] leading-relaxed text-text-secondary">
                          {band.description}
                        </span>
                      )}
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </>
      )}
      <PhDisclaimerNote className="pt-2 text-[10px] leading-relaxed" />
    </section>
  );
}

export function CareNoteCard({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  return (
    <section
      className={cn(
        "grid grid-cols-[auto_1fr] items-start gap-2.5 rounded-lg bg-chip-bg/60 p-3 lg:flex lg:items-center",
        className,
      )}
    >
      <span className="flex size-9 shrink-0 items-center justify-center rounded-md bg-primary text-white">
        <Info size={16} aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[12px] font-bold text-text-primary">{t("careNote.title")}</p>
        <p className="pt-0.5 text-[11px] leading-relaxed text-text-secondary">{t("careNote.body")}</p>
      </div>
      <Link
        to="/scan"
        className="col-span-2 flex shrink-0 items-center justify-center gap-1 rounded-md bg-surface px-3 py-2 text-[11px] font-semibold text-primary-dark shadow-xs hover:bg-background-alt"
      >
        <ScanLine size={13} aria-hidden="true" />
        {t("careNote.cta")}
      </Link>
    </section>
  );
}

const BUYING_FACTS = [
  { key: "shipping", icon: Truck, tone: "bg-chip-bg text-primary-dark" },
  { key: "payment", icon: CreditCard, tone: "bg-secondary/30 text-secondary-text-on" },
  { key: "tracking", icon: Package, tone: "bg-success-bg text-success-text" },
] as const;

export function BuyingFacts({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  return (
    <section className={cn("rounded-lg bg-surface p-4 shadow-brand-sm md:p-5", className)}>
      <h2 className="text-[16px] font-bold text-text-primary md:text-center md:text-[18px]">{t("buying.title")}</h2>
      <p className="pt-0.5 text-[11px] text-text-secondary md:text-center md:text-[12px]">{t("buying.subtitle")}</p>
      <ul className="grid gap-2 pt-3 md:grid-cols-3 md:gap-3 md:pt-4">
        {BUYING_FACTS.map(({ key, icon: Icon, tone }) => (
          <li key={key} className="flex gap-2.5 rounded-lg bg-background-alt/60 p-3 md:flex-col md:gap-0 md:p-3.5">
            <span className={cn("flex size-9 shrink-0 items-center justify-center rounded-lg md:mb-2", tone)}>
              <Icon size={16} aria-hidden="true" />
            </span>
            <span className="min-w-0">
              <span className="block text-[12px] font-bold leading-snug text-text-primary md:text-[13px]">
                {t(`buying.items.${key}.title`)}
              </span>
              <span className="block pt-0.5 text-[11px] leading-relaxed text-text-secondary md:pt-1">
                {t(`buying.items.${key}.body`)}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
