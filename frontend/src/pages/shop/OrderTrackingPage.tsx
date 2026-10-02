import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router";
import {
  Bike,
  Check,
  CheckCircle2,
  Copy,
  FileText,
  FlaskConical,
  LayoutDashboard,
  MapPin,
  MessageSquare,
  Package,
  Phone,
  Snowflake,
  Star,
  Truck,
} from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { MOCK_ORDER as O, MOCK_ORDER_MAP_IMAGE, formatVnd } from "./mockData";

/**
 * `/orders/:orderId` — Đặt hàng thành công & Theo dõi đơn hàng.
 *
 * Dựng theo bản WEB (`16:3252`, 1280×2063): banner thành công, timeline giao hàng 4 bước
 * nằm ngang + thẻ tài xế, rồi 2 cột dưới (chi tiết kiện hàng / hướng dẫn chuẩn bị + bảo hiểm).
 * Dưới `lg` xếp 1 cột, timeline chuyển dọc. Dữ liệu mock, xem `mockData.ts`.
 *
 * Khu vực bản đồ live-tracking dùng đúng ảnh map TĨNH cắt từ file Figma (xem
 * `MOCK_ORDER_MAP_IMAGE`) — KHÔNG nhúng map provider thật vì stack chưa chốt nhà cung cấp
 * bản đồ nào. Text thay thế của ảnh nói rõ đây là bản dựng giao diện.
 */
export function OrderTrackingPage() {
  const { t } = useTranslation("shop");
  const navigate = useNavigate();

  return (
    <div className="flex flex-col gap-5 px-4 py-5 lg:px-0">
      {/* Banner thành công */}
      <section className="rounded-2xl bg-secondary/15 p-5 shadow-brand-md">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-start">
          <span className="relative flex size-12 shrink-0 items-center justify-center rounded-2xl bg-success text-white">
            <Check size={26} strokeWidth={3} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="flex flex-wrap items-center gap-2 text-[22px] font-bold text-text-primary">
              {t("order.successTitle")}
              <span className="rounded-full bg-success-bg px-2.5 py-0.5 text-[10px] font-bold text-success-text">
                {O.slaBadge}
              </span>
            </h1>
            <p className="max-w-[560px] pt-1.5 text-[13px] leading-relaxed text-text-secondary">{O.thanksBody}</p>
          </div>
          <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
            <button
              type="button"
              className="flex items-center justify-center gap-1.5 rounded-xl bg-surface px-4 py-2.5 text-[12px] font-semibold text-text-primary shadow-xs hover:bg-background-alt"
            >
              <FileText size={14} aria-hidden="true" />
              {t("order.invoiceCta")}
            </button>
            <button
              type="button"
              onClick={() => { void navigate("/dashboard"); }}
              className="flex items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-4 py-2.5 text-[12px] font-bold text-white hover:bg-primary"
            >
              <LayoutDashboard size={14} aria-hidden="true" />
              {t("order.dashboardCta")}
            </button>
          </div>
        </div>

        <dl className="mt-4 grid gap-4 rounded-xl bg-surface/80 p-4 sm:grid-cols-2 xl:grid-cols-4">
          <div>
            <dt className="text-[10px] font-bold tracking-[0.4px] text-text-tertiary">{t("order.codeLabel")}</dt>
            <dd className="flex items-center gap-1.5 pt-1 text-[13px] font-bold text-primary-dark">
              {O.code}
              <Copy size={12} className="text-text-tertiary" aria-hidden="true" />
            </dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold tracking-[0.4px] text-text-tertiary">{t("order.placedLabel")}</dt>
            <dd className="pt-1 text-[13px] font-semibold text-text-primary">{O.placedAtLabel}</dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold tracking-[0.4px] text-text-tertiary">{t("order.methodLabel")}</dt>
            <dd className="flex items-center gap-1.5 pt-1 text-[13px] font-semibold text-text-primary">
              <CheckCircle2 size={13} className="text-success" aria-hidden="true" />
              {O.paymentLabel}
            </dd>
          </div>
          <div>
            <dt className="text-[10px] font-bold tracking-[0.4px] text-text-tertiary">{t("order.etaLabel")}</dt>
            <dd className="pt-1 text-[13px] font-bold text-success-text">{O.etaLabel}</dd>
          </div>
        </dl>
      </section>

      {/* Tiến trình giao hàng */}
      <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
        <div className="flex flex-col gap-2 lg:flex-row lg:items-start lg:justify-between">
          <div className="flex items-start gap-3">
            <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
              <Truck size={18} aria-hidden="true" />
            </span>
            <div>
              <h2 className="text-[16px] font-bold text-text-primary">{t("order.trackingTitle")}</h2>
              <p className="text-[11px] text-text-secondary">{O.trackingSubtitle}</p>
            </div>
          </div>
          <span className="shrink-0 rounded-full bg-secondary/25 px-3 py-1 text-[11px] font-semibold text-secondary-text-on">
            {O.courierDistance}
          </span>
        </div>

        {/* Stepper: ngang trên desktop, dọc trên mobile */}
        <ol className="flex flex-col gap-4 pt-6 lg:flex-row lg:gap-0">
          {O.steps.map((step, i) => (
            <li key={step.title} className="relative flex flex-1 gap-3 lg:flex-col lg:items-center lg:text-center">
              {i < O.steps.length - 1 ? (
                <span
                  className={cn(
                    "absolute left-4 top-9 h-[calc(100%-0.5rem)] w-0.5 lg:left-1/2 lg:top-4 lg:h-0.5 lg:w-full",
                    step.state === "done" ? "bg-primary" : "bg-border",
                  )}
                  aria-hidden="true"
                />
              ) : null}
              <span
                className={cn(
                  "relative z-10 flex size-8 shrink-0 items-center justify-center rounded-full",
                  step.state === "done"
                    ? "bg-success text-white"
                    : step.state === "current"
                      ? "bg-primary text-white"
                      : "bg-background-alt text-text-tertiary",
                )}
              >
                {step.state === "done" ? (
                  <Check size={15} strokeWidth={3} aria-hidden="true" />
                ) : step.state === "current" ? (
                  <Bike size={15} aria-hidden="true" />
                ) : (
                  <MapPin size={15} aria-hidden="true" />
                )}
              </span>
              <span className="min-w-0 lg:pt-2">
                <span
                  className={cn(
                    "block text-[12px] font-bold",
                    step.state === "current" ? "text-primary" : "text-text-primary",
                  )}
                >
                  {step.title}
                </span>
                <span className="block text-[11px] text-text-secondary">{step.time}</span>
              </span>
            </li>
          ))}
        </ol>

        {/* Bản đồ + tài xế */}
        <div className="mt-6 flex flex-col gap-4 lg:flex-row">
          <div className="relative min-h-[220px] flex-1 overflow-hidden rounded-2xl bg-background-alt">
            <img
              src={MOCK_ORDER_MAP_IMAGE}
              alt={t("order.mapPlaceholder")}
              className="h-full min-h-[220px] w-full object-cover"
            />
            <span className="absolute left-3 top-3 rounded-full bg-surface/95 px-2.5 py-1 text-[10px] font-semibold text-text-primary shadow-xs">
              {O.courier.driverLabel}
            </span>
            <span className="absolute right-3 top-3 rounded-full bg-primary px-2.5 py-1 text-[10px] font-semibold text-white">
              {O.courier.speedLabel}
            </span>
            <div className="absolute inset-x-3 bottom-3 flex items-center gap-2 rounded-xl bg-surface/95 px-3 py-2 shadow-xs">
              <span className="flex size-7 shrink-0 items-center justify-center rounded-lg bg-secondary/30 text-secondary-text-on">
                <MapPin size={13} aria-hidden="true" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block text-[10px] text-text-tertiary">{t("order.destinationLabel")}</span>
                <span className="block truncate text-[11px] font-semibold text-text-primary">
                  {O.courier.destinationLabel}
                </span>
              </span>
              <span className="shrink-0 text-[13px] font-bold text-primary-dark">{O.courier.etaShort}</span>
            </div>
          </div>

          <div className="shrink-0 rounded-2xl bg-background-alt/60 p-4 lg:w-[260px]">
            <p className="text-[12px] font-bold text-text-primary">{O.courier.teamLabel}</p>
            <div className="mt-3 flex items-center gap-2.5 rounded-xl bg-surface p-2.5">
              <img src={O.courier.photoUrl} alt="" className="size-10 shrink-0 rounded-lg object-cover" />
              <div className="min-w-0">
                <p className="truncate text-[12px] font-bold text-text-primary">{O.courier.name}</p>
                <p className="truncate text-[10px] text-text-secondary">{O.courier.role}</p>
                <p className="flex items-center gap-1 pt-0.5 text-[10px] font-semibold text-secondary-text-on">
                  <Star size={10} className="text-secondary" fill="currentColor" aria-hidden="true" />
                  {O.courier.ratingLabel}
                </p>
              </div>
            </div>
            <dl className="flex flex-col gap-2 pt-3 text-[11px]">
              <div className="flex justify-between gap-2">
                <dt className="text-text-secondary">{t("order.vehicleLabel")}</dt>
                <dd className="text-right font-semibold text-text-primary">{O.courier.vehicle}</dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-text-secondary">{t("order.coolingLabel")}</dt>
                <dd className="flex items-center gap-1 text-right font-semibold text-success-text">
                  <Snowflake size={11} aria-hidden="true" />
                  {O.courier.coolingLabel}
                </dd>
              </div>
              <div className="flex justify-between gap-2">
                <dt className="text-text-secondary">{t("order.departedLabel")}</dt>
                <dd className="text-right font-semibold text-text-primary">{O.courier.departedAt}</dd>
              </div>
            </dl>
            <div className="flex gap-2 pt-3">
              <button
                type="button"
                className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-surface px-2 py-2 text-[11px] font-semibold text-text-primary shadow-xs hover:bg-background-alt"
              >
                <Phone size={12} aria-hidden="true" />
                {t("order.callDriver")}
              </button>
              <button
                type="button"
                className="flex flex-1 items-center justify-center gap-1.5 rounded-xl bg-primary-dark px-2 py-2 text-[11px] font-semibold text-white hover:bg-primary"
              >
                <MessageSquare size={12} aria-hidden="true" />
                {t("order.messageDriver")}
              </button>
            </div>
          </div>
        </div>
      </section>

      {/* 2 cột dưới */}
      <div className="flex flex-col gap-5 lg:flex-row lg:items-start">
        {/* Kiện hàng */}
        <section className="min-w-0 flex-1 rounded-2xl bg-surface p-5 shadow-brand-md">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-[16px] font-bold text-text-primary">
              {t("order.itemsTitle", { count: O.items.length })}
            </h2>
            <span className="flex items-center gap-1.5 rounded-full bg-background-alt px-2.5 py-1 text-[10px] font-semibold text-text-secondary">
              <Package size={11} aria-hidden="true" />
              {O.packageLabel}
            </span>
          </div>

          <ul className="flex flex-col gap-3 pt-4">
            {O.items.map((item) => (
              <li key={item.id} className="flex gap-3 rounded-xl bg-background-alt/60 p-3">
                <img src={item.imageUrl} alt="" className="size-14 shrink-0 rounded-lg object-cover" />
                <div className="min-w-0 flex-1">
                  <p className="text-[13px] font-bold leading-snug text-text-primary">{item.name}</p>
                  <p className="truncate text-[11px] text-text-secondary">{item.subtitle}</p>
                  <p className="pt-1 text-[10px] text-text-tertiary">{item.qtyLabel}</p>
                  <p className="text-[10px] font-semibold text-success-text">{item.note}</p>
                </div>
                <div className="shrink-0 text-right">
                  {item.isGift ? (
                    <span className="mb-1 block rounded bg-success-bg px-1.5 py-0.5 text-[9px] font-bold text-success-text">
                      {t("order.giftBadge")}
                    </span>
                  ) : null}
                  <span className="block text-[13px] font-bold text-primary-dark">{formatVnd(item.price)}</span>
                  {item.compareAtPrice ? (
                    <s className="block text-[10px] text-text-tertiary">{formatVnd(item.compareAtPrice)}</s>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>

          <dl className="flex flex-col gap-2 border-t border-border pt-4 text-[12px]">
            <div className="flex justify-between">
              <dt className="text-text-secondary">{t("order.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(O.subtotal)}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-text-secondary">{O.voucherLabel}</dt>
              <dd className="font-semibold text-danger">
                -{formatVnd(O.voucherAmount)} {O.voucherPercent}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-text-secondary">{t("order.shippingLabel")}</dt>
              <dd className="font-semibold text-success-text">{O.shippingLabel}</dd>
            </div>
          </dl>

          <div className="flex items-end justify-between border-t border-border pt-3">
            <span>
              <span className="block text-[15px] font-bold text-text-primary">{t("order.totalLabel")}</span>
              <span className="block text-[10px] text-text-tertiary">{O.vatNote}</span>
            </span>
            <span className="text-[24px] font-bold text-primary-dark">{formatVnd(O.total)}</span>
          </div>

          <div className="mt-4 rounded-xl bg-background-alt/60 p-3">
            <p className="text-[12px] font-bold text-text-primary">{t("order.recipientTitle")}</p>
            <div className="grid gap-2 pt-2 sm:grid-cols-2">
              <p className="text-[11px]">
                <span className="block text-text-tertiary">{t("order.recipientLabel")}</span>
                <span className="font-semibold text-text-primary">{O.recipient.name}</span>
              </p>
              <p className="text-[11px]">
                <span className="block text-text-tertiary">{t("order.addressLabel")}</span>
                <span className="font-semibold text-text-primary">{O.recipient.address}</span>
              </p>
            </div>
            <p className="mt-2 rounded-lg bg-surface px-2.5 py-2 text-[11px] text-text-secondary">{O.driverNote}</p>
          </div>
        </section>

        {/* Chuẩn bị + bảo hiểm */}
        <div className="flex w-full shrink-0 flex-col gap-4 lg:w-[340px]">
          <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
            <div className="flex items-start gap-2.5">
              <span className="flex size-9 shrink-0 items-center justify-center rounded-xl bg-secondary/25 text-secondary-text-on">
                <FlaskConical size={17} aria-hidden="true" />
              </span>
              <div>
                <h2 className="text-[15px] font-bold leading-snug text-text-primary">{O.prepTitle}</h2>
                <p className="text-[10px] font-semibold text-primary">{O.prepSubtitle}</p>
              </div>
            </div>
            <p className="pt-3 text-[11px] leading-relaxed text-text-secondary">{O.prepIntro}</p>
            <ol className="flex flex-col gap-3 pt-3">
              {O.prepSteps.map((s, i) => (
                <li key={s.title} className="flex gap-2.5">
                  <span className="flex size-6 shrink-0 items-center justify-center rounded-full bg-chip-bg text-[11px] font-bold text-primary-dark">
                    {i + 1}
                  </span>
                  <span className="min-w-0">
                    <span className="block text-[12px] font-bold text-text-primary">{s.title}</span>
                    <span className="block pt-0.5 text-[11px] leading-relaxed text-text-secondary">{s.body}</span>
                  </span>
                </li>
              ))}
            </ol>
            <div className="mt-4 flex items-center gap-2.5 rounded-xl bg-background-alt/60 p-2.5">
              <img src={O.pointsImageUrl} alt="" className="size-9 shrink-0 rounded-lg object-cover" />
              <div className="min-w-0">
                <p className="text-[11px] font-bold text-text-primary">{O.pointsTitle}</p>
                <p className="text-[10px] leading-snug text-text-secondary">{O.pointsBody}</p>
              </div>
            </div>
          </section>

          <section className="rounded-2xl bg-primary-dark p-5 text-white">
            <span className="text-[9px] font-bold tracking-[0.5px] text-on-primary-muted">{O.insuranceEyebrow}</span>
            <h2 className="pt-1 text-[17px] font-bold leading-snug">{O.insuranceTitle}</h2>
            <p className="pt-2 text-[11px] leading-relaxed text-on-primary-muted">{O.insuranceBody}</p>
            <button
              type="button"
              className="mt-4 w-full rounded-xl bg-secondary px-4 py-2.5 text-[12px] font-bold text-secondary-text-on hover:bg-secondary-light"
            >
              {O.insuranceCta}
            </button>
            <p className="pt-2.5 text-center text-[10px] text-on-primary-muted">{O.insuranceSupport}</p>
          </section>
        </div>
      </div>
    </div>
  );
}
