import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import { Link, useParams } from "react-router";
import {
  Banknote,
  Check,
  CircleCheckBig,
  ClipboardList,
  Copy,
  House,
  Landmark,
  Package,
  PackageCheck,
  PackageSearch,
  Truck,
  Wallet,
  X,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { isApiError } from "@/shared/api";
import { MoMoGlyph } from "@/shared/assets/icons/MoMoGlyph";
import { cn } from "@/shared/lib/cn";
import { formatDate } from "@/shared/lib/format/formatDate";
import { EmptyState, ErrorState, SkeletonLoader, toast } from "@/shared/ui";
import { isShopId, useShopOrder } from "@/features/shop";
import { SHOP_ORDER_STAGES, formatVnd, isShopPaymentMethodId, type ShopPaymentMethodId } from "./shopFormat";
import { ProductGlyph, ShippingFee } from "./shopUi";

/**
 * `/orders/:orderId` — Đặt hàng thành công & theo dõi đơn (design `Web - Đặt hàng thành công &
 * Theo dõi đơn hàng` / mobile cùng tên).
 *
 * Mọi dữ liệu là `GET /api/v1/orders/{id}`: mã đơn, thời điểm đặt, phương thức, trạng thái
 * (enum `ck_shop_order_status`), dòng hàng, tạm tính/giảm giá/phí vận chuyển/tổng, người nhận.
 * Trang tự làm mới 30 giây/lần.
 *
 * Đã gỡ khỏi mockup (không có API / tuyên bố chưa kiểm chứng): "giao hỏa tốc 2H", giờ dự kiến
 * giao, bản đồ GPS + tài xế + phương tiện, giờ của từng bước, tải hoá đơn VAT, quà tặng, mã
 * giảm giá CHAO_SEN, ghi chú tài xế, "chuẩn bị trước khi nhận cát", C-Points, "gói bảo hiểm
 * sinh học", "lịch chăm sóc", nút "theo dõi thời gian thực".
 */

const STAGE_ICONS: Record<(typeof SHOP_ORDER_STAGES)[number], LucideIcon> = {
  PENDING_PAYMENT: ClipboardList,
  PAID: Wallet,
  PACKING: Package,
  SHIPPING: Truck,
  DELIVERED: PackageCheck,
};

const PAYMENT_ICONS: Record<ShopPaymentMethodId, LucideIcon | null> = {
  COD: Banknote,
  MOMO: null,
  BANK_TRANSFER: Landmark,
};

function OrderSkeleton() {
  return (
    <div className="flex flex-col gap-5" aria-hidden="true">
      <SkeletonLoader className="h-[220px] rounded-2xl" />
      <SkeletonLoader className="h-[160px] rounded-2xl" />
      <SkeletonLoader className="h-[300px] rounded-2xl" />
    </div>
  );
}

function InfoCell({ label, children, className }: { label: string; children: ReactNode; className?: string }) {
  return (
    <div className={cn("min-w-0", className)}>
      <dt className="text-[11px] font-bold uppercase tracking-wide text-text-tertiary">{label}</dt>
      <dd className="pt-1 text-[13px] font-semibold text-text-primary">{children}</dd>
    </div>
  );
}

export function OrderTrackingPage() {
  const { t } = useTranslation("shop");
  const { orderId } = useParams<{ orderId: string }>();
  const orderQuery = useShopOrder(orderId);

  const notFound = !isShopId(orderId) || (isApiError(orderQuery.error) && orderQuery.error.status === 404);
  if (notFound) {
    return (
      <EmptyState
        icon={<PackageSearch size={22} />}
        title={t("order.notFound")}
        description={t("order.notFoundBody")}
        action={
          <Link
            to="/shop"
            className="inline-flex rounded-xl bg-primary-dark px-5 py-3 text-[13px] font-bold text-white hover:bg-primary"
          >
            {t("order.continueShopping")}
          </Link>
        }
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    );
  }
  if (orderQuery.isPending) return <OrderSkeleton />;
  if (orderQuery.isError) {
    return (
      <ErrorState
        title={t("order.error")}
        onRetry={() => {
          void orderQuery.refetch();
        }}
        className="rounded-2xl bg-surface shadow-brand-md"
      />
    );
  }

  const order = orderQuery.data;
  const cancelled = order.status === "CANCELLED";
  const stageIndex = (SHOP_ORDER_STAGES as readonly string[]).indexOf(order.status);
  const statusKey = cancelled || stageIndex >= 0 ? order.status : "UNKNOWN";
  const paymentKey = isShopPaymentMethodId(order.paymentMethod) ? order.paymentMethod : null;
  const PaymentIcon = paymentKey ? PAYMENT_ICONS[paymentKey] : Wallet;
  const lastIndex = SHOP_ORDER_STAGES.length - 1;

  const copyCode = () => {
    void navigator.clipboard
      .writeText(order.orderCode)
      .then(() => toast.success(t("order.codeCopied")))
      .catch(() => undefined);
  };

  return (
    <div className="flex flex-col gap-5">
      {/* ---------- Đầu trang: kết quả đặt hàng + thông tin chính ---------- */}
      <section className="rounded-2xl bg-surface p-5 shadow-brand-md md:p-6">
        <div className="flex flex-col items-center gap-4 text-center md:flex-row md:items-start md:text-left">
          <span
            className={cn(
              "flex size-16 shrink-0 items-center justify-center rounded-2xl",
              cancelled ? "bg-danger-bg text-danger-text" : "bg-success-bg text-success-text",
            )}
          >
            {cancelled ? <X size={30} aria-hidden="true" /> : <CircleCheckBig size={30} aria-hidden="true" />}
          </span>
          <div className="min-w-0 flex-1">
            <h1 className="text-[22px] font-bold leading-tight text-text-primary md:text-[26px]">
              {t(cancelled ? "order.cancelledTitle" : "order.successTitle")}
            </h1>
            <p className="pt-1.5 text-[13px] leading-relaxed text-text-secondary md:text-[14px]">
              {t(cancelled ? "order.cancelledBody" : "order.successBody")}
            </p>
          </div>
          <div className="hidden shrink-0 gap-2 md:flex">
            <Link
              to="/dashboard"
              className="flex items-center gap-1.5 rounded-xl bg-chip-bg px-4 py-2.5 text-[13px] font-semibold text-primary-dark hover:bg-info"
            >
              <House size={15} aria-hidden="true" />
              {t("order.backHome")}
            </Link>
            <Link
              to="/shop"
              className="rounded-xl bg-primary-dark px-4 py-2.5 text-[13px] font-bold text-white hover:bg-primary"
            >
              {t("order.continueShopping")}
            </Link>
          </div>
        </div>

        <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-4 rounded-xl bg-background-alt/60 p-4 md:grid-cols-4">
          <InfoCell label={t("order.codeLabel")} className="col-span-2 md:col-span-1">
            <span className="flex items-center gap-1">
              <span className="truncate font-bold text-primary-dark">#{order.orderCode}</span>
              <button
                type="button"
                onClick={copyCode}
                aria-label={t("order.copyCode")}
                className="flex size-7 shrink-0 items-center justify-center rounded-md text-text-tertiary hover:bg-surface hover:text-primary-dark"
              >
                <Copy size={13} aria-hidden="true" />
              </button>
            </span>
          </InfoCell>
          <InfoCell label={t("order.placedLabel")}>{formatDate(order.createdAt, "HH:mm, dd/MM/yyyy")}</InfoCell>
          <InfoCell label={t("order.methodLabel")}>
            {paymentKey ? t(`payment.methods.${paymentKey}.name`) : t("order.paymentUnknown")}
          </InfoCell>
          <InfoCell label={t("order.statusLabel")}>
            <span
              className={cn(
                "inline-flex rounded-full px-2.5 py-0.5 text-[12px] font-bold",
                cancelled
                  ? "bg-danger-bg text-danger-text"
                  : statusKey === "DELIVERED"
                    ? "bg-success-bg text-success-text"
                    : "bg-chip-bg text-primary-dark",
              )}
            >
              {t(`order.status.${statusKey}`)}
            </span>
          </InfoCell>
        </dl>
      </section>

      {/* ---------- Tiến trình xử lý: các bước = enum trạng thái của API ---------- */}
      <section className="rounded-2xl bg-surface p-5 shadow-brand-md md:p-6" aria-live="polite">
        <div className="flex items-start gap-3">
          <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-chip-bg text-primary-dark">
            <Truck size={18} aria-hidden="true" />
          </span>
          <div className="min-w-0">
            <h2 className="text-[16px] font-bold text-text-primary">{t("order.trackingTitle")}</h2>
            <p className="pt-0.5 text-[12px] leading-relaxed text-text-secondary">{t("order.trackingDisclaimer")}</p>
          </div>
        </div>

        {cancelled ? (
          <p className="mt-5 flex items-center gap-2 rounded-xl bg-danger-bg p-4 text-[13px] font-semibold text-danger-text">
            <X size={16} aria-hidden="true" />
            {t("order.cancelledNote")}
          </p>
        ) : stageIndex < 0 ? (
          <p className="mt-5 rounded-xl bg-background-alt p-4 text-[13px] text-text-secondary">
            {t("order.status.UNKNOWN")}
          </p>
        ) : (
          <ol className="mt-6 flex flex-col md:flex-row">
            {SHOP_ORDER_STAGES.map((stage, index) => {
              const Icon = STAGE_ICONS[stage];
              const done = index < stageIndex || (index === lastIndex && stageIndex === lastIndex);
              const current = index === stageIndex && !done;
              const isLast = index === lastIndex;
              return (
                <li
                  key={stage}
                  className="relative flex gap-3 pb-6 last:pb-0 md:flex-1 md:flex-col md:items-center md:gap-2 md:pb-0 md:text-center"
                  aria-current={index === stageIndex ? "step" : undefined}
                >
                  {/* Đường nối tới bước sau: dọc ở mobile, ngang từ `md`. */}
                  {!isLast ? (
                    <span
                      aria-hidden="true"
                      className={cn(
                        "absolute left-[17px] top-9 h-[calc(100%-36px)] w-0.5 md:left-[calc(50%+22px)] md:top-[17px] md:h-0.5 md:w-[calc(100%-44px)]",
                        index < stageIndex ? "bg-success" : "bg-border",
                      )}
                    />
                  ) : null}
                  <span
                    className={cn(
                      "relative z-[1] flex size-9 shrink-0 items-center justify-center rounded-full",
                      done
                        ? "bg-success text-white"
                        : current
                          ? "bg-primary-dark text-white ring-4 ring-chip-bg"
                          : "bg-background-alt text-text-tertiary",
                    )}
                  >
                    {done ? <Check size={16} aria-hidden="true" /> : <Icon size={16} aria-hidden="true" />}
                  </span>
                  <span className="min-w-0 pt-1.5 md:px-1 md:pt-0">
                    <span
                      className={cn(
                        "block text-[13px] leading-snug",
                        current
                          ? "font-bold text-primary-dark"
                          : done
                            ? "font-semibold text-text-primary"
                            : "text-text-tertiary",
                      )}
                    >
                      {t(`order.status.${stage}`)}
                    </span>
                    {index === stageIndex ? (
                      <span className="block pt-0.5 text-[11px] font-semibold text-text-secondary">
                        {t("order.currentStage")}
                      </span>
                    ) : null}
                  </span>
                </li>
              );
            })}
          </ol>
        )}
      </section>

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_340px] lg:items-start">
        {/* ---------- Kiện hàng chi tiết + tổng tiền ---------- */}
        <section className="rounded-2xl bg-surface p-5 shadow-brand-md md:p-6">
          <h2 className="text-[16px] font-bold text-text-primary">
            {t("order.itemsTitle", { count: order.lines.length })}
          </h2>
          <ul className="mt-4 flex flex-col gap-3">
            {order.lines.map((line) => (
              <li key={line.product.id} className="flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
                <ProductGlyph
                  sku={line.product.sku}
                  imageUrl={line.product.imageUrl}
                  className="size-14 shrink-0"
                  iconSize={22}
                />
                <div className="min-w-0 flex-1">
                  <p className="text-[14px] font-semibold leading-snug text-text-primary">{line.product.name}</p>
                  <p className="pt-0.5 text-[12px] text-text-secondary">
                    {t("order.lineMeta", {
                      sku: line.product.sku,
                      count: line.quantity,
                      price: formatVnd(line.product.priceVnd),
                    })}
                  </p>
                </div>
                <span className="shrink-0 text-[14px] font-bold text-primary-dark">{formatVnd(line.totalVnd)}</span>
              </li>
            ))}
          </ul>
          <dl className="mt-5 flex flex-col gap-2 text-[13px]">
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("order.subtotalLabel")}</dt>
              <dd className="font-semibold text-text-primary">{formatVnd(order.subtotalVnd)}</dd>
            </div>
            {order.discountVnd > 0 ? (
              <div className="flex justify-between gap-4">
                <dt className="text-text-secondary">{t("order.discountLabel")}</dt>
                <dd className="font-semibold text-danger-text">-{formatVnd(order.discountVnd)}</dd>
              </div>
            ) : null}
            <div className="flex justify-between gap-4">
              <dt className="text-text-secondary">{t("order.shippingLabel")}</dt>
              <dd className="font-semibold text-text-primary">
                <ShippingFee value={order.shippingFeeVnd} />
              </dd>
            </div>
            <div className="mt-2 flex items-end justify-between gap-4 border-t border-border pt-3">
              <dt className="text-[15px] font-bold text-text-primary">{t("order.totalLabel")}</dt>
              <dd className="text-[24px] font-bold leading-none text-primary-dark">{formatVnd(order.totalVnd)}</dd>
            </div>
          </dl>
          {/* Hộp phương thức thanh toán như design mobile — chỉ phương thức + ghi chú, KHÔNG suy ra
              "đã thanh toán" (API không có trạng thái thanh toán riêng). */}
          <div className="mt-4 flex items-center gap-3 rounded-xl bg-background-alt/60 p-3">
            <span className="flex size-10 shrink-0 items-center justify-center rounded-xl bg-surface text-primary-dark">
              {PaymentIcon ? <PaymentIcon size={18} aria-hidden="true" /> : <MoMoGlyph size={28} />}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block text-[13px] font-bold text-text-primary">
                {paymentKey ? t(`payment.methods.${paymentKey}.name`) : t("order.paymentUnknown")}
              </span>
              {paymentKey ? (
                <span className="block pt-0.5 text-[12px] leading-relaxed text-text-secondary">
                  {t(`order.paymentNote.${paymentKey}`)}
                </span>
              ) : null}
            </span>
          </div>
        </section>

        {/* ---------- Thông tin nhận hàng ---------- */}
        <section className="rounded-2xl bg-surface p-5 shadow-brand-md">
          <h2 className="text-[16px] font-bold text-text-primary">{t("order.recipientTitle")}</h2>
          <dl className="mt-4 flex flex-col gap-3 text-[13px]">
            <div>
              <dt className="text-[12px] text-text-tertiary">{t("order.recipientLabel")}</dt>
              <dd className="pt-0.5 font-semibold text-text-primary">{order.receiverName}</dd>
            </div>
            <div>
              <dt className="text-[12px] text-text-tertiary">{t("order.phoneLabel")}</dt>
              <dd className="pt-0.5 font-semibold text-text-primary">{order.receiverPhone}</dd>
            </div>
            <div>
              <dt className="text-[12px] text-text-tertiary">{t("order.addressLabel")}</dt>
              <dd className="whitespace-pre-line break-words pt-0.5 font-semibold leading-relaxed text-text-primary">
                {order.shippingAddress}
              </dd>
            </div>
          </dl>
        </section>
      </div>

      {/* Mobile: hai lối ra như design (bỏ "theo dõi thời gian thực" — không có dữ liệu). */}
      <div className="flex flex-col gap-2.5 md:hidden">
        <Link
          to="/shop"
          className="flex items-center justify-center rounded-xl bg-primary-dark px-4 py-3.5 text-[14px] font-bold text-white hover:bg-primary"
        >
          {t("order.continueShopping")}
        </Link>
        <Link
          to="/dashboard"
          className="flex items-center justify-center gap-1.5 rounded-xl bg-chip-bg px-4 py-3.5 text-[14px] font-semibold text-primary-dark hover:bg-info"
        >
          <House size={15} aria-hidden="true" />
          {t("order.backHome")}
        </Link>
      </div>
    </div>
  );
}
