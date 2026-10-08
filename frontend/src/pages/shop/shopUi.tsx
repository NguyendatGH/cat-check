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

/**
 * Các khối dùng chung của module Cửa hàng.
 *
 * Ảnh sản phẩm: chỉ hiển thị `ProductResponse.imageUrl` khi API trả. Bộ seed hiện tại để trống
 * trường này, nên các thẻ dùng ô biểu tượng trung tính (icon theo họ `sku` thật). KHÔNG mượn
 * ảnh render trong mockup Figma (`shared/assets/images/web-shop/product-*.jpg`): bao bì trong
 * đó in tuyên bố chưa kiểm chứng ("99.9% dust free"…), ảnh gói định kỳ là "SmartSand Pro"
 * (không phải SKU thật) và ảnh CleanBox là một thiết bị điện tử chứ không phải khay cát.
 *
 * Khối pH lấy nhãn/ngưỡng/màu TỪ `entities/ph-bands` (`GET /reference/ph-bands`, endpoint
 * công khai) đúng p6 §6.7.3/4 và quyết định #5b: cửa hàng KHÔNG tự đặt lại nhãn pH.
 */

/** Icon theo họ SKU của catalogue thật (`SMARTSAND-*`, `SUBSCRIPTION-*`, `CLEANBOX-*`). */
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
  /** `ProductResponse.imageUrl` — có thì hiển thị ảnh thật, không thì ô biểu tượng. */
  imageUrl?: string | null;
  className?: string;
  iconSize?: number;
  showSku?: boolean;
}) {
  const Icon = glyphForSku(sku);
  return (
    <span
      className={cn(
        "relative flex items-center justify-center overflow-hidden rounded-xl bg-gradient-to-br from-chip-bg to-background-alt",
        className,
      )}
    >
      {imageUrl ? (
        <img src={imageUrl} alt="" className="size-full object-cover" loading="lazy" />
      ) : (
        <Icon size={iconSize} className="text-primary-dark/70" aria-hidden="true" />
      )}
      {showSku ? (
        <span className="absolute bottom-2 left-2 right-2 truncate rounded-full bg-surface/90 px-2 py-0.5 text-center text-[10px] font-bold text-primary-dark">
          {sku}
        </span>
      ) : null}
    </span>
  );
}

/** Chip tồn kho — đọc trực tiếp `stockQuantity` của API, không có nhãn "best seller"/"hot". */
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

/** Giá bán + giá so sánh + % giảm, tất cả tính từ `priceVnd`/`compareAtPriceVnd` thật. */
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

/**
 * Bộ tăng/giảm số lượng dùng chung (giỏ hàng, chi tiết sản phẩm). `max` là
 * `maxOrderQuantity(stockQuantity)`; nút "+" khoá khi chạm trần, "−" khoá ở 1.
 */
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
  /** Đang chờ máy chủ xác nhận số lượng mới — khoá cả hai nút, hiện spinner thay số. */
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

/**
 * Lối vào `/cart` dưới `lg` (mobile không có cột giỏ hàng). Chỉ hiện khi giỏ thật có hàng;
 * số món và tổng tiền là của `GET /api/v1/cart`.
 */
export function CartShortcut({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  const cartQuery = useShopCart();
  const cart = cartQuery.data;
  if (!cart || cart.lines.length === 0) return null;
  return (
    <Link
      to="/cart"
      className={cn(
        "flex items-center gap-3 rounded-2xl bg-surface p-3 shadow-brand-md hover:bg-background-alt lg:hidden",
        className,
      )}
    >
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
        <ShoppingCart size={18} aria-hidden="true" />
      </span>
      <span className="min-w-0 flex-1">
        <span className="block text-[13px] font-bold text-text-primary">
          {t("cartShortcut.title", { count: cart.lines.length })}
        </span>
        <span className="block text-[12px] text-text-secondary">
          {t("cartShortcut.total", { total: formatVnd(cart.totalVnd) })}
        </span>
      </span>
      <span className="flex shrink-0 items-center gap-0.5 text-[12px] font-semibold text-primary-dark">
        {t("cartShortcut.cta")}
        <ChevronRight size={14} aria-hidden="true" />
      </span>
    </Link>
  );
}

/** Phí vận chuyển đúng con số `shippingFeeVnd` của máy chủ (0 ⇒ "Miễn phí"). */
export function ShippingFee({ value }: { value: number }) {
  const { t } = useTranslation("shop");
  return value === 0 ? (
    <span className="text-success-text">{t("cartPanel.freeShipping")}</span>
  ) : (
    <>{formatVnd(value)}</>
  );
}

/** Nhãn khoảng của một dải pH — dựng từ `phMin`/`phMax` của API, không hard-code ngưỡng. */
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

/**
 * Thang pH tham chiếu.
 *
 * `compact` (cột phải của hero): thanh gradient + chú giải nhãn.
 * Mặc định: thêm từng dải kèm khoảng và mô tả của API. Mô tả có placeholder `{0}` (dành cho
 * một giá trị pH đo được) bị bỏ qua — ở cửa hàng không có số đo nào để điền.
 */
export function PhBandScale({ compact = false, className }: { compact?: boolean; className?: string }) {
  const { t } = useTranslation("shop");
  const bandsQuery = usePhBands();
  const rangeLabel = useBandRangeLabel();
  const bands = (bandsQuery.data ?? [])
    .filter((band) => typeof band.phMin === "number" || typeof band.phMax === "number")
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder);

  return (
    <section className={cn("rounded-2xl bg-surface p-5 shadow-brand-md", className)}>
      <h2 className="flex items-center gap-2 text-[16px] font-bold text-text-primary">
        <Droplet size={16} className="shrink-0 text-primary-dark" aria-hidden="true" />
        {t("phScale.title")}
      </h2>
      {!compact ? <p className="pt-1.5 text-[12px] leading-relaxed text-text-secondary">{t("phScale.body")}</p> : null}

      {bandsQuery.isPending ? (
        <p className="pt-4 text-[12px] text-text-tertiary">{t("phScale.loading")}</p>
      ) : bands.length === 0 ? (
        <p className="pt-4 text-[12px] text-text-tertiary">{t("phScale.error")}</p>
      ) : (
        <>
          <PhGaugeBar bands={bands} className="pt-4" />
          {compact ? (
            <PhRangeLegend bands={bands} className="pt-3" />
          ) : (
            <ul className="grid gap-2 pt-4 md:grid-cols-2">
              {bands.map((band) => {
                const style = phTokenStyle(band.colorToken);
                return (
                  <li key={band.code} className="flex gap-2.5 rounded-xl bg-background-alt/60 p-3">
                    <span className={cn("mt-1 size-2.5 shrink-0 rounded-full", style.solid)} aria-hidden="true" />
                    <span className="min-w-0">
                      <span className="block text-[13px] font-bold leading-snug text-text-primary">{band.label}</span>
                      <span
                        className={cn(
                          "mt-1 inline-flex rounded-full px-2 py-0.5 text-[11px] font-semibold",
                          style.bg,
                          style.text,
                        )}
                      >
                        {rangeLabel(band.phMin, band.phMax)}
                      </span>
                      {band.description.includes("{0}") ? null : (
                        <span className="block pt-1.5 text-[11px] leading-relaxed text-text-secondary">
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
      {/* `text-[11px]` tường minh: `PhDisclaimerNote` ghép `text-small text-text-tertiary` qua `cn()`,
          và tailwind-merge mặc định coi `text-small` là MÀU chữ nên bỏ mất cỡ chữ (rơi về 16px). */}
      <PhDisclaimerNote className="pt-3 text-[11px] leading-relaxed" />
    </section>
  );
}

/** Nhắc phạm vi sản phẩm (quan sát pH, không chẩn đoán) + đường dẫn tới trình quét có thật. */
export function CareNoteCard({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  return (
    <section className={cn("flex flex-col gap-3 rounded-2xl bg-chip-bg/60 p-4 lg:flex-row lg:items-center", className)}>
      <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-primary text-white">
        <Info size={18} aria-hidden="true" />
      </span>
      <div className="min-w-0 flex-1">
        <p className="text-[13px] font-bold text-text-primary">{t("careNote.title")}</p>
        <p className="pt-0.5 text-[12px] leading-relaxed text-text-secondary">{t("careNote.body")}</p>
      </div>
      <Link
        to="/scan"
        className="flex shrink-0 items-center justify-center gap-1.5 rounded-xl bg-surface px-4 py-2.5 text-[12px] font-semibold text-primary-dark shadow-xs hover:bg-background-alt"
      >
        <ScanLine size={14} aria-hidden="true" />
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

/**
 * Ba điều hệ thống cửa hàng THỰC SỰ làm — ở đúng vị trí khối "cam kết/kiểm định" của mockup
 * (khối đó là tuyên bố chưa kiểm chứng nên đã bỏ). Bố cục theo design: mobile là các hàng
 * icon-trái, web là lưới thẻ icon-trên với tiêu đề căn giữa.
 */
export function BuyingFacts({ className }: { className?: string }) {
  const { t } = useTranslation("shop");
  return (
    <section className={cn("rounded-2xl bg-surface p-5 shadow-brand-md md:p-8", className)}>
      <h2 className="text-[18px] font-bold text-text-primary md:text-center md:text-[22px]">{t("buying.title")}</h2>
      <p className="pt-1 text-[12px] text-text-secondary md:text-center md:text-[13px]">{t("buying.subtitle")}</p>
      <ul className="grid gap-3 pt-4 md:grid-cols-3 md:gap-4 md:pt-6">
        {BUYING_FACTS.map(({ key, icon: Icon, tone }) => (
          <li key={key} className="flex gap-3 rounded-xl bg-background-alt/60 p-4 md:flex-col md:gap-0 md:p-5">
            <span className={cn("flex size-10 shrink-0 items-center justify-center rounded-xl md:mb-3", tone)}>
              <Icon size={18} aria-hidden="true" />
            </span>
            <span className="min-w-0">
              <span className="block text-[13px] font-bold leading-snug text-text-primary md:text-[15px]">
                {t(`buying.items.${key}.title`)}
              </span>
              <span className="block pt-1 text-[12px] leading-relaxed text-text-secondary md:pt-1.5">
                {t(`buying.items.${key}.body`)}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
