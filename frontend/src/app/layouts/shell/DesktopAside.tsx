import { NavLink, useLocation } from "react-router";
import { isNavItemActive } from "./navActive";
import { useTranslation } from "react-i18next";
import { Sparkles } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { CatAvatar } from "@/entities/cat";
import { useSessionStore } from "@/entities/user";
import { useCatList } from "@/features/cat";
import { formatRemainingSeconds, useCreditBalance } from "@/features/credit";
import webLogo from "@/shared/assets/images/web-dashboard/logo-lockup.png";
import iconChevron from "@/shared/assets/icons/web-dashboard/side-chevron.svg";
import iconPromo from "@/shared/assets/icons/web-dashboard/side-promo.svg";
import navOverview from "@/shared/assets/icons/web-dashboard/nav-overview.svg";
import navScan from "@/shared/assets/icons/web-dashboard/nav-scan.svg";
import navCat from "@/shared/assets/icons/web-dashboard/nav-cat.svg";
import navHistory from "@/shared/assets/icons/web-dashboard/nav-history.svg";
import navMap from "@/shared/assets/icons/web-dashboard/nav-map.svg";
import navCommunity from "@/shared/assets/icons/web-dashboard/nav-community.svg";
import navShop from "@/shared/assets/icons/web-dashboard/nav-shop.svg";
import navSettings from "@/shared/assets/icons/web-dashboard/nav-settings.svg";
import { ROUTE_PATTERNS } from "../../router/routes";

/**
 * Sidebar desktop — các mục theo Figma `Web - 02` node 16:7516 (Nav), thứ tự của kiểm kê màn
 * W1 §"Sidebar trái": Tổng quan → Quét màu cát → Lịch sử & Phân tích → Hồ sơ Bé Mèo →
 * Bản đồ Thú y → Cộng đồng → Cửa hàng Cát → Cài đặt.
 */
const DESKTOP_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.dashboard, icon: navOverview, labelKey: "nav.overview", end: true },
  { to: ROUTE_PATTERNS.scan, icon: navScan, labelKey: "nav.scanColor", end: false },
  { to: ROUTE_PATTERNS.historyRedirect, icon: navHistory, labelKey: "nav.historyAnalytics", end: false },
  { to: ROUTE_PATTERNS.catsList, icon: navCat, labelKey: "nav.catProfile", end: false },
  { to: ROUTE_PATTERNS.map, icon: navMap, labelKey: "nav.vetMap", end: false },
  { to: ROUTE_PATTERNS.community, icon: navCommunity, labelKey: "nav.community", end: false },
  { to: ROUTE_PATTERNS.shop, icon: navShop, labelKey: "nav.shop", end: false },
  { to: ROUTE_PATTERNS.settings, icon: navSettings, labelKey: "nav.settings", end: false },
] as const;

/**
 * "Trợ lý AI" — owner đã thêm module AI có backend thật (`/api/v1/ai/chat`), mục này GIỮ.
 * Dùng icon Lucide `Sparkles`: asset `welcome/deco-sparkle.svg` của thiết kế thực chất vẽ một
 * chevron-left nên nằm giữa menu trông như nút "quay lại".
 */
const DESKTOP_NAV_EXTRA = [{ to: ROUTE_PATTERNS.assistant, icon: Sparkles, labelKey: "nav.assistant" }] as const;

/**
 * Cỡ chữ viết dạng `text-[14px] leading-5` chứ KHÔNG dùng token `text-caption`: `cn()` (twMerge
 * mặc định) không biết `caption` là cỡ chữ nên xếp nó chung nhóm MÀU chữ với `text-white` /
 * `text-text-secondary` và XOÁ nó — menu từng render 16px (cỡ body) thay vì 14px của Figma.
 */
const NAV_ITEM_CLASS = "flex h-11 items-center gap-3 rounded-xl px-4 text-[14px] leading-5 transition-colors";
const NAV_ACTIVE_CLASS = "bg-primary-dark font-semibold text-white shadow-[0px_4px_6px_-1px_rgba(13,54,154,0.2)]";
const NAV_IDLE_CLASS = "font-medium text-text-secondary hover:bg-background-alt";

/**
 * Icon sidebar vẽ bằng CSS mask thay vì `<img>`: SVG xuất từ Figma đã "nướng" sẵn màu
 * (`nav-overview.svg` trắng vì đang ACTIVE trong thiết kế, 7 icon còn lại `#444652`), dùng
 * `<img>` thì icon active thành trắng-trên-trắng khi đổi route. Mask giữ hình dạng asset và tô
 * theo `currentColor`.
 *
 * URL PHẢI bọc nháy kép: Vite inline SVG < 4KB thành data URI chứa nháy ĐƠN; `url()` không bọc
 * nháy thì trình duyệt bỏ cả khai báo `mask-image` và icon thành ô vuông đặc.
 */
function NavMaskIcon({ src, className }: { src: string; className?: string }) {
  const maskUrl = `url("${src.replace(/"/g, "%22")}")`;
  return (
    <span
      aria-hidden="true"
      className={cn("inline-block shrink-0 bg-current", className)}
      style={{
        maskImage: maskUrl,
        WebkitMaskImage: maskUrl,
        maskRepeat: "no-repeat",
        WebkitMaskRepeat: "no-repeat",
        maskPosition: "center",
        WebkitMaskPosition: "center",
        maskSize: "contain",
        WebkitMaskSize: "contain",
      }}
    />
  );
}

/**
 * Thẻ "bé mèo đang theo dõi" ở đầu sidebar (Figma 16:7504) — nguồn `GET /cats`, mèo chính
 * trước. Chevron dẫn tới `/cats` (hub đa mèo): app không có state "mèo đang chọn" toàn cục,
 * mọi trang lấy `:catId` từ URL, nên không dựng dropdown giả.
 */
function CatSwitcherCard() {
  const { t } = useTranslation("common");
  const user = useSessionStore((s) => s.user);
  // Khách chưa đăng nhập: `GET /cats` chắc chắn 403, đừng gọi.
  const { data, isPending } = useCatList("ACTIVE", Boolean(user));
  const cats = data?.items ?? [];
  const cat = cats.find((c) => c.isPrimary) ?? cats.at(0);

  if (!user) return null;

  if (isPending) {
    return (
      <div className="px-4 pb-2">
        <div aria-hidden="true" className="h-14 animate-pulse rounded-xl bg-background-alt" />
      </div>
    );
  }

  if (!cat) {
    return (
      <div className="px-4 pb-2">
        <NavLink
          to={ROUTE_PATTERNS.catNew}
          className="flex h-14 items-center gap-2 rounded-xl bg-background-alt px-3 text-caption text-text-secondary hover:bg-chip-bg"
        >
          <span className="flex size-10 shrink-0 items-center justify-center rounded-full bg-chip-bg text-h3 font-bold text-primary-dark">
            +
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate font-bold text-text-primary">{t("webShell.switcherEmpty")}</span>
            <span className="block truncate text-[11px] leading-[14px]">{t("webShell.switcherEmptyCta")}</span>
          </span>
        </NavLink>
      </div>
    );
  }

  const years = cat.ageMonths == null ? null : Math.round((cat.ageMonths / 12) * 10) / 10;

  return (
    <div className="px-4 pb-2">
      <NavLink
        to={ROUTE_PATTERNS.catsList}
        className="flex h-14 items-center gap-2 rounded-xl bg-background-alt px-2 hover:bg-chip-bg"
      >
        <span className="relative flex size-10 shrink-0 items-center justify-center">
          <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-10" />
          <span className="absolute bottom-0 right-0 size-3 rounded-full border-2 border-surface bg-success" />
        </span>
        <span className="min-w-0 flex-1">
          <span className="block truncate text-caption font-bold text-text-primary">
            {cat.breedName ? t("webShell.switcherTitle", { name: cat.name, breed: cat.breedName }) : cat.name}
          </span>
          <span className="block truncate text-[11px] leading-[14px] text-text-secondary">
            {years == null ? t("webShell.switcherWatching") : t("webShell.switcherMeta", { age: years })}
          </span>
        </span>
        <img src={iconChevron} alt="" className="h-[13.425px] w-[6.75px] shrink-0" />
      </NavLink>
    </div>
  );
}

/**
 * Thẻ ghim đáy sidebar (Figma 16:7559 — thẻ vàng "CÁT CHỈ THỊ PH / Gói … / Còn lại N ngày").
 *
 * Thiết kế in tên gói và số ngày thay khay — không endpoint nào trả hai thứ đó. Nay thẻ đọc
 * H2 `GET /credits/balance` thật: còn lượt (credit trong lô còn hạn) ⇒ số lượt + hạn lô gần
 * nhất, bấm vào xem `/credits`; chỉ khi credit lẫn lượt thử đều bằng 0 mới hiện "Hết lượt quét"
 * kèm CTA nhập mã. Lỗi tải ⇒ chỉ còn CTA, KHÔNG đoán số dư.
 */
function CreditsCard() {
  const { t } = useTranslation("common");
  const { t: tCredit } = useTranslation("credit");
  const { data, isPending, isError } = useCreditBalance();

  if (isPending) {
    return <div aria-hidden="true" className="h-[104px] animate-pulse rounded-xl bg-secondary/30" />;
  }

  const header = (
    <div className="flex items-center justify-between gap-2">
      <span className="text-overline font-bold tracking-[0.4px] text-secondary-text-on">
        {t("webShell.promoLabel")}
      </span>
      <img src={iconPromo} alt="" className="h-[15.75px] w-[16.5px] shrink-0" />
    </div>
  );

  // Lỗi tải (kể cả lỗi refetch khi còn dữ liệu cũ) ⇒ không dùng số dư nào cả.
  const balance = isError ? undefined : data;
  const credits = balance?.availableBalance ?? 0;
  if (balance && credits > 0) {
    const nearest = balance.batches.find((b) => b.remainingAmount > 0);
    return (
      <NavLink to={ROUTE_PATTERNS.credits} className="block rounded-xl bg-secondary p-4 hover:bg-secondary-light">
        {header}
        <p className="pt-1 text-body font-bold text-text-primary">
          {t("webShell.creditsAvailable", { count: credits })}
        </p>
        {nearest ? (
          <p className="pt-0.5 text-small text-text-primary/80">
            {t("webShell.creditsExpiry", { remaining: formatRemainingSeconds(nearest.remainingSeconds, tCredit) })}
          </p>
        ) : null}
      </NavLink>
    );
  }

  const trial = balance?.trialScansRemaining ?? 0;
  return (
    <div className="rounded-xl bg-secondary p-4">
      {header}
      {balance ? (
        <p className="pt-1 text-body font-bold text-text-primary">
          {trial > 0 ? t("webShell.creditsTrial", { count: trial }) : t("webShell.promoTitle")}
        </p>
      ) : null}
      <p className="pt-0.5 text-small text-text-primary/80">{t("webShell.promoNote")}</p>
      <NavLink
        to={ROUTE_PATTERNS.creditsActivate}
        className="mt-3 flex min-h-11 items-center justify-center rounded-lg bg-surface px-3 text-caption font-bold text-primary-dark shadow-xs hover:bg-background-alt"
      >
        {t("webShell.promoCta")}
      </NavLink>
    </div>
  );
}

/**
 * Sidebar desktop — Figma `Web - 02` node 16:7500 "Aside", rộng đúng 288px: logo (h 83) · thẻ
 * mèo · Nav (mỗi mục 256×44, bước 48) · thẻ lượt quét ghim đáy.
 *
 * Hai tầng có chủ đích: lớp ngoài là CỘT căng hết chiều cao trang (nền trắng + viền phải liền
 * một dải tới chân trang dài), lớp trong `sticky top-0 h-dvh` giữ menu luôn trong khung nhìn
 * khi cuộn. Trước đây chỉ có một lớp `sticky h-dvh` nên trang dài hơn khung nhìn thì phía dưới
 * sidebar trắng trơn và viền phải đứt giữa chừng.
 */
export function DesktopAside() {
  const { t } = useTranslation("common");
  const signedIn = Boolean(useSessionStore((s) => s.user));
  const { pathname } = useLocation();

  return (
    <div className="hidden w-72 shrink-0 border-r border-border bg-surface lg:block">
      <div className="sticky top-0 flex h-dvh flex-col">
        <div className="flex h-[83px] shrink-0 items-center justify-center px-4">
          <img src={webLogo} alt={t("app.name")} className="h-[53px] w-40 object-contain" />
        </div>

        <CatSwitcherCard />

        <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-4 pt-1" aria-label={t("nav.mainLabel")}>
          {DESKTOP_NAV_ITEMS.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                cn(NAV_ITEM_CLASS, isNavItemActive(item.to, pathname, isActive) ? NAV_ACTIVE_CLASS : NAV_IDLE_CLASS)
              }
            >
              <NavMaskIcon src={item.icon} className="size-[18px]" />
              {t(item.labelKey)}
            </NavLink>
          ))}
          {DESKTOP_NAV_EXTRA.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) => cn(NAV_ITEM_CLASS, isActive ? NAV_ACTIVE_CLASS : NAV_IDLE_CLASS)}
            >
              <item.icon size={18} className="shrink-0" aria-hidden="true" />
              {t(item.labelKey)}
            </NavLink>
          ))}
        </nav>

        {/* Khách chưa đăng nhập: H2 cần phiên — không gọi, không hiện thẻ. */}
        {signedIn ? (
          <div className="shrink-0 p-4">
            <CreditsCard />
          </div>
        ) : null}
      </div>
    </div>
  );
}
