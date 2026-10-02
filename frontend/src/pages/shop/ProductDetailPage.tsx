import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate } from "react-router";
import {
  ArrowRight,
  Award,
  BadgeCheck,
  Check,
  CheckCircle2,
  ChevronRight,
  ClipboardCheck,
  Droplet,
  HelpCircle,
  Layers,
  Leaf,
  Maximize2,
  MessageCircle,
  Minus,
  PawPrint,
  Plus,
  RefreshCw,
  ScanLine,
  ShieldCheck,
  ShoppingCart,
  Star,
  Truck,
  Wind,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import {
  MOCK_COLOR_INDICATORS,
  MOCK_PACK_SIZES,
  MOCK_PRODUCT_DETAIL as P,
  MOCK_PURCHASE_MODES,
  MOCK_REVIEWS,
  MOCK_SPEC_TABLE,
  MOCK_VET_QUOTE,
  formatVnd,
} from "./mockData";
import { useShopCart } from "./useShopCart";

/**
 * `/shop/products/:productId` — Chi tiết sản phẩm.
 *
 * Từ `lg`: bản WEB (`16:1592`, 1280×3126) — hero 2 cột (gallery trái / mua hàng phải), rồi
 * các section full-width: cơ chế đổi màu, quy trình 4 bước, bảng so sánh gói, đánh giá.
 * Dưới `lg`: bản MOBILE (`1:4974`, 390×2504) — xếp 1 cột và thay khối combo CleanBox bằng
 * "Hình thức mua hàng" + "Kích thước & Công thức" + bảng "Tiêu chuẩn công thức" + trích dẫn
 * bác sĩ, với thanh mua dính đáy. Dữ liệu mock, xem `mockData.ts`.
 *
 * ⚠️ Bảng "màu cát ↔ tình trạng" KHÔNG phải nguyên văn thiết kế: mục "Màu Đỏ Gạch / Cam"
 * trong Figma hứa phát hiện tế bào máu, mâu thuẫn quyết định #8 của owner ("Chỉ pH. Không
 * phát hiện máu") nên đã viết lại sang ngôn ngữ quan sát pH — xem ghi chú đầu `mockData.ts`.
 */

const SPEC_ICONS = [Leaf, Droplet, Wind, ShieldCheck] as const;
const STEP_ICONS = [Layers, PawPrint, ScanLine, ClipboardCheck] as const;

const TONE_STYLES = {
  success: { chip: "bg-success-bg text-success-text", dot: "bg-success" },
  primary: { chip: "bg-chip-bg text-primary-dark", dot: "bg-primary" },
  warning: { chip: "bg-warning-bg text-warning-text", dot: "bg-warning" },
  danger: { chip: "bg-danger-bg text-danger-text", dot: "bg-danger" },
} as const;

export function ProductDetailPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();
  const add = useShopCart((s) => s.add);
  const [activeImage, setActiveImage] = useState(0);
  const [quantity, setQuantity] = useState(1);
  /** Hai lựa chọn chỉ có ở bản mobile — thuần hiển thị, KHÔNG đổi giá đang hiện. */
  const [purchaseMode, setPurchaseMode] = useState<string>(MOCK_PURCHASE_MODES[0].id);
  const [packSize, setPackSize] = useState<string>(MOCK_PACK_SIZES[1].id);

  const addToCart = () => {
    add({
      id: `line-${P.id}`,
      name: P.title,
      subtitle: P.topBadges[0],
      imageUrl: P.gallery[0],
      unitPrice: P.price,
      quantity,
    });
  };

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0">
      {/* Breadcrumb */}
      <nav className="flex flex-wrap items-center gap-1 text-[12px] text-text-secondary">
        <Link to="/shop" className="hover:text-primary-dark">
          {P.breadcrumb[0]}
        </Link>
        {P.breadcrumb.slice(1).map((crumb, i) => (
          <span key={crumb} className="flex items-center gap-1">
            <ChevronRight size={12} aria-hidden="true" />
            <span className={i === P.breadcrumb.length - 2 ? "font-semibold text-text-primary" : ""}>{crumb}</span>
          </span>
        ))}
      </nav>

      {/* Hero 2 cột */}
      <div className="flex flex-col gap-6 lg:flex-row">
        {/* Gallery */}
        <div className="min-w-0 lg:w-[46%]">
          <div className="relative overflow-hidden rounded-2xl bg-surface shadow-brand-md">
            <img src={P.gallery[activeImage]} alt="" className="aspect-[4/3] w-full object-cover" />
            <span className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-surface/95 px-2.5 py-1 text-[10px] font-bold text-text-primary shadow-xs">
              <ShieldCheck size={11} className="text-success" aria-hidden="true" />
              {P.imageBadge}
            </span>
            <span className="absolute bottom-3 right-3 flex items-center gap-1 rounded-full bg-surface/95 px-2.5 py-1 text-[10px] font-semibold text-text-secondary shadow-xs">
              <Maximize2 size={11} aria-hidden="true" />
              {t("detail.galleryZoom")}
            </span>
            <span className="absolute bottom-3 left-3 rounded-lg bg-surface/90 px-2.5 py-1.5 text-[10px] font-semibold leading-tight text-text-secondary shadow-xs">
              {t("detail.galleryCaptionLine1")}
              <span className="block">{t("detail.galleryCaptionLine2")}</span>
            </span>
          </div>
          {/* Thiết kế có 4 thumbnail và ảnh lớn là phần tử riêng -> bỏ qua `gallery[0]`. */}
          <div className="grid grid-cols-4 gap-2 pt-2">
            {P.gallery.slice(1).map((src, i) => (
              <button
                key={src}
                type="button"
                onClick={() => { setActiveImage(i + 1); }}
                className={cn(
                  "overflow-hidden rounded-xl border-2 transition-colors",
                  i + 1 === activeImage ? "border-primary" : "border-transparent hover:border-border",
                )}
              >
                <img src={src} alt="" className="aspect-[4/3] w-full object-cover" />
              </button>
            ))}
          </div>

          {/* Dải chỉ số + tư vấn */}
          <div className="mt-3 rounded-2xl bg-surface p-4 shadow-brand-md">
            <p className="flex flex-wrap items-center gap-2 border-b border-border pb-2.5 text-[11px] font-bold text-primary-dark">
              <Droplet size={12} aria-hidden="true" />
              {P.indicatorStrip}
              <span className="font-normal text-text-tertiary">{P.indicatorStripNote}</span>
            </p>
            <div className="flex items-start gap-3 pt-3">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
                <MessageCircle size={16} aria-hidden="true" />
              </span>
              <div className="min-w-0 flex-1">
                <p className="text-[13px] font-bold text-text-primary">{P.consultTitle}</p>
                <p className="pt-0.5 text-[11px] text-text-secondary">{P.consultBody}</p>
              </div>
              <span className="shrink-0 rounded-full bg-secondary px-2 py-0.5 text-[10px] font-bold text-secondary-text-on">
                {P.consultBadge}
              </span>
            </div>
            <div className="flex flex-col gap-2 pt-3 sm:flex-row">
              <button
                type="button"
                className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-background-alt px-3 py-2.5 text-[12px] font-semibold text-text-primary hover:bg-chip-bg"
              >
                <HelpCircle size={14} aria-hidden="true" />
                {t("detail.faqCta")}
              </button>
              <button
                type="button"
                className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-3 py-2.5 text-[12px] font-bold text-white hover:bg-primary"
              >
                <MessageCircle size={14} aria-hidden="true" />
                {t("detail.chatCta")}
              </button>
            </div>
          </div>
        </div>

        {/* Cột mua hàng */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap gap-2">
            {P.topBadges.map((b) => (
              <span key={b} className="rounded-full bg-chip-bg px-2.5 py-1 text-[10px] font-semibold text-primary-dark">
                {b}
              </span>
            ))}
          </div>
          <h1 className="pt-3 text-[24px] font-bold leading-tight text-text-primary lg:text-[28px]">{P.title}</h1>
          <p className="pt-2.5 text-[13px] leading-relaxed text-text-secondary">{P.description}</p>

          <div className="flex flex-wrap items-center gap-2 pt-3">
            <span className="flex items-center gap-0.5" aria-hidden="true">
              {[0, 1, 2, 3, 4].map((i) => (
                <Star key={i} size={13} className="text-secondary" fill="currentColor" />
              ))}
            </span>
            <span className="text-[13px] font-bold text-text-primary">{P.rating}</span>
            <span className="text-[12px] text-text-secondary">{P.ratingNote}</span>
          </div>
          <p className="flex items-center gap-1.5 pt-1.5 text-[12px] text-success-text">
            <ShieldCheck size={13} aria-hidden="true" />
            {P.clinicNote}
          </p>

          {/* Giá */}
          <div className="mt-4 flex flex-wrap items-center gap-3 rounded-2xl bg-surface p-4 shadow-brand-md">
            <span className="text-[26px] font-bold text-primary-dark">{formatVnd(P.price)}</span>
            <s className="text-[13px] text-text-tertiary">{formatVnd(P.compareAtPrice)}</s>
            <span className="rounded-lg bg-danger-bg px-2 py-1 text-[11px] font-bold text-danger-text">
              {P.discountLabel}
            </span>
            <span className="flex items-center gap-1.5 rounded-lg bg-chip-bg px-2.5 py-1.5 text-[11px] font-semibold text-primary-dark">
              <Truck size={12} aria-hidden="true" />
              {P.shipNote}
            </span>
            <p className="w-full text-[11px] text-text-tertiary">{P.priceFootnote}</p>
          </div>

          {/* Hình thức mua hàng — chỉ bản mobile (`1:4974`) */}
          <fieldset className="pt-5 lg:hidden">
            <div className="flex items-center justify-between gap-3">
              <legend className="contents">
                <span className="text-[17px] font-bold text-text-primary">{t("detail.purchaseModeTitle")}</span>
              </legend>
              <span className="flex items-center gap-1.5 text-[12px] font-semibold text-secondary-text-on">
                <RefreshCw size={13} aria-hidden="true" />
                {t("detail.purchaseModeNote")}
              </span>
            </div>
            <div className="flex flex-col gap-2.5 pt-3">
              {MOCK_PURCHASE_MODES.map((mode) => {
                const selected = purchaseMode === mode.id;
                return (
                  <label
                    key={mode.id}
                    className={cn(
                      "flex cursor-pointer gap-3 rounded-xl p-3.5 transition-colors",
                      selected
                        ? "border-l-4 border-primary-dark bg-surface shadow-brand-md"
                        : "bg-surface/70 hover:bg-surface",
                    )}
                  >
                    <input
                      type="radio"
                      name="purchase-mode"
                      value={mode.id}
                      checked={selected}
                      onChange={() => { setPurchaseMode(mode.id); }}
                      className="sr-only"
                    />
                    <span
                      aria-hidden="true"
                      className={cn(
                        "mt-0.5 flex size-4 shrink-0 items-center justify-center rounded-full border-2",
                        selected ? "border-primary-dark" : "border-chip-bg",
                      )}
                    >
                      {selected ? <span className="size-2 rounded-full bg-primary-dark" /> : null}
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-start justify-between gap-2">
                        <span
                          className={cn(
                            "text-[14px] font-bold leading-snug",
                            selected ? "text-text-primary" : "text-text-secondary",
                          )}
                        >
                          {mode.title}
                        </span>
                        {mode.badge ? (
                          <span className="flex shrink-0 items-center gap-1 rounded-full bg-secondary px-2.5 py-1 text-[11px] font-bold text-secondary-text-on">
                            <Star size={10} fill="currentColor" aria-hidden="true" />
                            {mode.badge}
                          </span>
                        ) : null}
                      </span>
                      <span className="block pt-1 text-[12px] leading-relaxed text-text-secondary">{mode.body}</span>
                      <span className="block pt-1.5 text-[15px] font-bold text-primary-dark">
                        {formatVnd(mode.price)}
                        <span className="pl-1 text-[12px] font-normal text-text-tertiary">{mode.priceUnit}</span>
                      </span>
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          {/* Kích thước & Công thức — chỉ bản mobile (`1:4974`) */}
          <fieldset className="pt-5 lg:hidden">
            <div className="flex items-center justify-between gap-3">
              <legend className="contents">
                <span className="text-[17px] font-bold text-text-primary">{t("detail.packSizeTitle")}</span>
              </legend>
              <button type="button" className="text-[12px] font-semibold text-primary hover:underline">
                {t("detail.packSizeGuide")}
              </button>
            </div>
            <div className="grid grid-cols-2 gap-2.5 pt-3">
              {MOCK_PACK_SIZES.map((size) => {
                const selected = packSize === size.id;
                return (
                  <label
                    key={size.id}
                    className={cn(
                      "relative cursor-pointer rounded-xl p-3 transition-colors",
                      selected ? "bg-chip-bg" : "bg-surface hover:bg-background-alt",
                    )}
                  >
                    <input
                      type="radio"
                      name="pack-size"
                      value={size.id}
                      checked={selected}
                      onChange={() => { setPackSize(size.id); }}
                      className="sr-only"
                    />
                    {selected ? (
                      <CheckCircle2
                        size={15}
                        className="absolute right-2.5 top-2.5 text-primary"
                        aria-hidden="true"
                      />
                    ) : null}
                    <span
                      className={cn(
                        "block pr-5 text-[13px] font-bold",
                        selected ? "text-primary-dark" : "text-text-primary",
                      )}
                    >
                      {size.title}
                    </span>
                    <span className="block pt-0.5 text-[12px] leading-snug text-text-secondary">{size.detail}</span>
                    <span
                      className={cn(
                        "block pt-2 text-[15px] font-bold",
                        selected ? "text-primary-dark" : "text-text-primary",
                      )}
                    >
                      {formatVnd(size.price)}
                    </span>
                  </label>
                );
              })}
            </div>
          </fieldset>

          {/* Combo — chỉ bản web (`16:1592`) */}
          <p className="hidden pt-4 text-[12px] font-semibold text-text-primary lg:block">{P.comboLabel}</p>
          <label className="mt-2 hidden cursor-pointer items-start gap-3 rounded-xl bg-surface p-3 shadow-xs lg:flex">
            <input type="radio" name="combo" className="mt-1 size-4 accent-[var(--color-primary)]" />
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-semibold text-text-primary">{P.comboOption.name}</span>
              <span className="block text-[11px] text-text-secondary">{P.comboOption.detail}</span>
            </span>
            <span className="shrink-0 text-[14px] font-bold text-primary-dark">{formatVnd(P.comboOption.price)}</span>
          </label>

          {/* Mua — bản web; bản mobile dùng thanh dính đáy ở cuối trang */}
          <div className="hidden flex-wrap items-center gap-2 pt-4 lg:flex">
            <span className="inline-flex items-center gap-1 rounded-xl bg-surface px-2 py-2 shadow-xs">
              <button
                type="button"
                aria-label={t("cartPanel.decrease")}
                onClick={() => { setQuantity((q) => Math.max(1, q - 1)); }}
                className="flex size-6 items-center justify-center rounded text-text-secondary hover:bg-background-alt"
              >
                <Minus size={13} aria-hidden="true" />
              </button>
              <span className="min-w-6 text-center text-[13px] font-bold text-text-primary">{quantity}</span>
              <button
                type="button"
                aria-label={t("cartPanel.increase")}
                onClick={() => { setQuantity((q) => q + 1); }}
                className="flex size-6 items-center justify-center rounded text-text-secondary hover:bg-background-alt"
              >
                <Plus size={13} aria-hidden="true" />
              </button>
            </span>
            <button
              type="button"
              onClick={addToCart}
              className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-chip-bg px-4 py-2.5 text-[13px] font-semibold text-primary-dark hover:bg-info"
            >
              <ShoppingCart size={14} aria-hidden="true" />
              {t("detail.addToCart")}
            </button>
            <button
              type="button"
              onClick={() => {
                addToCart();
                void navigate("/checkout");
              }}
              className="flex flex-1 items-center justify-center rounded-xl bg-primary px-4 py-2.5 text-[13px] font-bold text-white shadow-brand-md hover:bg-primary-dark"
            >
              {t("detail.buyNow")}
            </button>
          </div>

          <p className="mt-2.5 hidden flex-wrap items-center justify-between gap-2 rounded-xl bg-secondary/15 px-3 py-2 text-[11px] font-semibold text-secondary-text-on lg:flex">
            <span>{P.subscribeNote}</span>
            <span>{P.subscribeSaving}</span>
          </p>

          <div className="grid grid-cols-2 gap-2 pt-3 sm:grid-cols-4">
            {P.specs.map((s, i) => {
              const Icon = SPEC_ICONS[i] ?? Droplet;
              return (
                <div key={s.title} className="rounded-xl bg-surface p-2.5 text-center shadow-xs">
                  <Icon size={14} className="mx-auto text-primary-dark" aria-hidden="true" />
                  <p className="pt-1 text-[11px] font-bold text-text-primary">{s.title}</p>
                  <p className="text-[10px] text-text-secondary">{s.body}</p>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      {/* Cơ chế đổi màu */}
      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <span className="text-[10px] font-bold tracking-[0.5px] text-primary">{P.mechanismEyebrow}</span>
        <div className="flex flex-col gap-2 pt-1 lg:flex-row lg:items-end lg:justify-between">
          <h2 className="max-w-[440px] text-[22px] font-bold leading-tight text-text-primary">{P.mechanismTitle}</h2>
          <p className="text-[11px] text-text-secondary">{P.mechanismRange}</p>
        </div>
        <div className="grid gap-3 pt-5 sm:grid-cols-2 xl:grid-cols-4">
          {MOCK_COLOR_INDICATORS.map((c) => {
            const tone = TONE_STYLES[c.tone];
            return (
              <div key={c.name} className="flex flex-col rounded-xl bg-background-alt/60 p-4">
                <div className="flex items-center justify-between">
                  <span className={cn("flex size-7 items-center justify-center rounded-lg", tone.chip)}>
                    <span className={cn("size-2.5 rounded-full", tone.dot)} aria-hidden="true" />
                  </span>
                  <span className={cn("rounded-full px-2 py-0.5 text-[10px] font-bold", tone.chip)}>
                    {c.statusLabel}
                  </span>
                </div>
                <p className="pt-2.5 text-[14px] font-bold text-text-primary">{c.name}</p>
                <p className="text-[11px] font-semibold text-text-secondary">{c.range}</p>
                <p className="flex-1 pt-2 text-[11px] leading-relaxed text-text-secondary">{c.body}</p>
                <p className={cn("mt-3 rounded-lg px-2 py-1.5 text-[10px] font-semibold", tone.chip)}>{c.footer}</p>
              </div>
            );
          })}
        </div>
      </section>

      {/* Quy trình 4 bước */}
      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <h2 className="text-[20px] font-bold text-text-primary">{t("detail.stepsTitle")}</h2>
        <div className="grid gap-3 pt-4 sm:grid-cols-2 xl:grid-cols-4">
          {P.steps.map((s, i) => {
            const StepIcon = STEP_ICONS[i] ?? Layers;
            return (
            <div key={s.no} className="rounded-xl bg-background-alt/60 p-4">
              <span className="flex items-center justify-between">
                <span className="text-[22px] font-bold text-border-strong">{s.no}</span>
                <StepIcon size={16} className="text-primary" aria-hidden="true" />
              </span>
              <p className="pt-1 text-[13px] font-bold text-text-primary">{s.title}</p>
              <p className="pt-1 text-[11px] leading-relaxed text-text-secondary">{s.body}</p>
            </div>
            );
          })}
        </div>
      </section>

      {/* So sánh gói */}
      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <h2 className="text-[20px] font-bold text-text-primary">{t("detail.comparisonTitle")}</h2>
        <div className="overflow-x-auto pt-4">
          <table className="w-full min-w-[640px] border-collapse text-[12px]">
            <thead>
              <tr className="border-b border-border">
                {P.comparisonHeaders.map((h, i) => (
                  <th
                    key={h}
                    className={cn(
                      "px-3 py-2.5 text-left font-bold",
                      i === 2 ? "text-primary-dark" : "text-text-primary",
                    )}
                  >
                    {h}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {P.comparisonRows.map((row) => (
                <tr key={row.label} className="border-b border-border/60">
                  <td className="px-3 py-2.5 text-text-secondary">{row.label}</td>
                  {row.values.map((v, i) => (
                    <td
                      key={`${row.label}-${String(i)}`}
                      className={cn("px-3 py-2.5", i === 1 ? "font-semibold text-primary-dark" : "text-text-primary")}
                    >
                      {v === "yes" ? (
                        <Check size={15} className="text-success" aria-label="Có" />
                      ) : (
                        v
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {/* Tiêu chuẩn công thức + trích dẫn bác sĩ — chỉ bản mobile (`1:4974`) */}
      <section className="rounded-2xl bg-surface p-4 shadow-brand-md lg:hidden">
        <h2 className="text-[17px] font-bold text-text-primary">{t("detail.specTableTitle")}</h2>
        <dl className="pt-1">
          {MOCK_SPEC_TABLE.map((row) => (
            <div
              key={row.label}
              className="flex items-start justify-between gap-4 border-b border-border/60 py-3 last:border-b-0 last:pb-0"
            >
              <dt className="shrink-0 text-[13px] text-text-secondary">{row.label}</dt>
              <dd
                className={cn(
                  "text-right text-[13px] font-bold",
                  row.tone === "success" ? "text-success-text" : "text-text-primary",
                )}
              >
                {row.value}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="rounded-2xl bg-chip-bg/60 p-4 lg:hidden">
        <div className="flex items-center gap-3">
          <img src={MOCK_VET_QUOTE.photoUrl} alt="" className="size-12 shrink-0 rounded-full object-cover" />
          <div className="min-w-0">
            <p className="flex items-center gap-1.5 text-[15px] font-bold text-text-primary">
              {MOCK_VET_QUOTE.name}
              <BadgeCheck size={15} className="shrink-0 text-primary" aria-hidden="true" />
            </p>
            <p className="text-[12px] text-text-secondary">{MOCK_VET_QUOTE.org}</p>
          </div>
        </div>
        <blockquote className="pt-3 text-[13px] italic leading-relaxed text-text-secondary">
          “{MOCK_VET_QUOTE.quote}”
        </blockquote>
        <button
          type="button"
          className="flex items-center gap-1.5 pt-3 text-[13px] font-bold text-primary hover:underline"
        >
          {MOCK_VET_QUOTE.cta}
          <ArrowRight size={14} aria-hidden="true" />
        </button>
      </section>

      {/* Đánh giá */}
      <section className="rounded-2xl bg-surface p-6 shadow-brand-md">
        <span className="text-[10px] font-bold tracking-[0.5px] text-primary">{P.reviewsEyebrow}</span>
        <div className="flex flex-col gap-3 pt-1 lg:flex-row lg:items-center lg:justify-between">
          <h2 className="text-[22px] font-bold leading-tight text-text-primary">{P.reviewsTitle}</h2>
          <div className="flex items-center gap-3">
            <span className="text-right">
              <span className="block text-[22px] font-bold text-primary-dark">{P.reviewsScore}</span>
              <span className="block text-[10px] text-text-secondary">{P.reviewsScoreNote}</span>
            </span>
            <button
              type="button"
              className="rounded-xl bg-chip-bg px-4 py-2 text-[12px] font-semibold text-primary-dark hover:bg-info"
            >
              {t("detail.writeReview")}
            </button>
          </div>
        </div>
        <div className="grid gap-4 pt-5 lg:grid-cols-3">
          {MOCK_REVIEWS.map((r) => (
            <article key={r.id} className="flex flex-col rounded-xl bg-background-alt/60 p-4">
              <div className="flex items-start gap-2">
                <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-white">
                  {r.initials}
                </span>
                <div className="min-w-0 flex-1">
                  <p className="truncate text-[12px] font-bold text-text-primary">{r.author}</p>
                  <p className="truncate text-[10px] text-text-secondary">{r.meta}</p>
                </div>
                <span className="shrink-0 text-[10px] text-text-tertiary">{r.time}</span>
              </div>
              <span className="flex gap-0.5 pt-2" aria-hidden="true">
                {[0, 1, 2, 3, 4].map((i) => (
                  <Star key={i} size={11} className="text-secondary" fill="currentColor" />
                ))}
              </span>
              <p className="flex-1 pt-2 text-[11px] leading-relaxed text-text-secondary">{r.body}</p>
              <img src={r.imageUrl} alt="" className="mt-3 aspect-[4/3] w-full rounded-lg object-cover" />
              <p className="flex items-center gap-1.5 pt-2 text-[10px] font-semibold text-success-text">
                <Award size={11} aria-hidden="true" />
                {r.caption}
              </p>
            </article>
          ))}
        </div>
      </section>

      {/* Thanh mua dính đáy — chỉ bản mobile (`1:4974`) */}
      <div className="sticky bottom-0 -mx-4 -mb-5 flex items-center gap-2.5 border-t border-border bg-surface px-4 py-3 shadow-top lg:hidden">
        <span className="inline-flex shrink-0 items-center gap-1 rounded-xl bg-background-alt px-1.5 py-1.5">
          <button
            type="button"
            aria-label={t("cartPanel.decrease")}
            onClick={() => { setQuantity((q) => Math.max(1, q - 1)); }}
            className="flex size-7 items-center justify-center rounded-lg text-text-secondary hover:bg-surface"
          >
            <Minus size={15} aria-hidden="true" />
          </button>
          <span className="min-w-5 text-center text-[14px] font-bold text-text-primary">{quantity}</span>
          <button
            type="button"
            aria-label={t("cartPanel.increase")}
            onClick={() => { setQuantity((q) => q + 1); }}
            className="flex size-7 items-center justify-center rounded-lg text-text-secondary hover:bg-surface"
          >
            <Plus size={15} aria-hidden="true" />
          </button>
        </span>
        <button
          type="button"
          onClick={addToCart}
          aria-label={t("detail.addToCart")}
          className="flex size-11 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark hover:bg-info"
        >
          <ShoppingCart size={18} aria-hidden="true" />
        </button>
        <button
          type="button"
          onClick={() => {
            addToCart();
            void navigate("/checkout");
          }}
          className="flex min-w-0 flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-4 py-3 text-[14px] font-bold text-white shadow-brand-md hover:bg-primary"
        >
          <span className="truncate">
            {t("detail.buyNowShort")} • {formatVnd(P.price * quantity)}
          </span>
        </button>
      </div>
    </div>
  );
}
