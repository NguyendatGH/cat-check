import { Outlet, NavLink, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import {
  LogoPawIcon,
  BellIcon,
  AvatarPersonIcon,
  NavHomeIcon,
  NavHistoryIcon,
  NavScanIcon,
  NavCommunityIcon,
  NavProfileIcon,
} from "@/shared/assets/icons/AppIcons";
import { useSessionStore } from "@/entities/user";
import webLogo from "@/shared/assets/images/web-dashboard/logo-lockup.png";
import iconSearch from "@/shared/assets/icons/web-dashboard/header-search.svg";
import iconBell from "@/shared/assets/icons/web-dashboard/header-bell.svg";
import iconAvatar from "@/shared/assets/icons/web-dashboard/header-avatar.svg";
import iconChevron from "@/shared/assets/icons/web-dashboard/side-chevron.svg";
import iconPromo from "@/shared/assets/icons/web-dashboard/side-promo.svg";
import navOverview from "@/shared/assets/icons/web-dashboard/nav-overview.svg";
import navScan from "@/shared/assets/icons/web-dashboard/nav-scan.svg";
import navHistory from "@/shared/assets/icons/web-dashboard/nav-history.svg";
import navCat from "@/shared/assets/icons/web-dashboard/nav-cat.svg";
import navMap from "@/shared/assets/icons/web-dashboard/nav-map.svg";
import navCommunity from "@/shared/assets/icons/web-dashboard/nav-community.svg";
import navShop from "@/shared/assets/icons/web-dashboard/nav-shop.svg";
import navSettings from "@/shared/assets/icons/web-dashboard/nav-settings.svg";
import { ROUTE_PATTERNS } from "../router/routes";

/** Bottom nav mobile — GIỮ NGUYÊN 5 mục của Figma 390px (node 1:2242). */
const MOBILE_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.dashboard, icon: NavHomeIcon, labelKey: "nav.dashboard" },
  { to: ROUTE_PATTERNS.historyRedirect, icon: NavHistoryIcon, labelKey: "nav.history" },
  { to: ROUTE_PATTERNS.scan, icon: NavScanIcon, labelKey: "nav.scan" },
  { to: ROUTE_PATTERNS.community, icon: NavCommunityIcon, labelKey: "nav.community" },
  { to: ROUTE_PATTERNS.settingsProfile, icon: NavProfileIcon, labelKey: "nav.profile" },
] as const;

/** Sidebar desktop — 8 mục theo Figma `Web - 02` node 16:7516 (Nav), đúng thứ tự thiết kế. */
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
 * Icon sidebar vẽ bằng CSS mask thay vì `<img>`.
 *
 * Lý do: SVG tải từ Figma đã "nướng" sẵn màu — `nav-overview.svg` là `fill="white"` (vì trong
 * thiết kế nó đang ở trạng thái ACTIVE trên viên nền xanh đậm), 7 icon còn lại là `#444652`.
 * Dùng `<img>` thì icon active sẽ trắng-trên-trắng khi đổi route. Mask giữ NGUYÊN hình dạng
 * thật của asset thiết kế nhưng cho phép tô theo `currentColor`.
 *
 * URL PHẢI bọc trong nháy kép: các SVG này nhỏ hơn `assetsInlineLimit` (4096B) nên Vite
 * inline chúng thành data URI `data:image/svg+xml,%3csvg ... width='15' ...`. Data URI đó
 * chứa dấu nháy ĐƠN, mà `url()` không bọc nháy thì cấm ký tự `'` — trình duyệt coi cả khai
 * báo `mask-image` là không hợp lệ và BỎ nó, kết quả là 8 icon sidebar biến thành ô vuông
 * đặc (`bg-current` không bị mask). Nháy kép an toàn vì Vite đã đổi hết `"` thành `'`.
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
 * Header mobile — khớp Figma "02. Trang chủ" node 1:2217 (khung 390px).
 * Logo tròn xanh + "CATCHECK", chuông thông báo, avatar. Ẩn từ `lg` trở lên.
 */
function AppHeader() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  return (
    <header className="sticky top-0 z-[var(--z-sticky-header)] w-full bg-[rgba(250,248,255,0.8)] shadow-[0px_1px_8px_0px_rgba(0,0,0,0.04)] backdrop-blur-md lg:hidden">
      <div className="flex h-14 items-center justify-between px-4">
        <div className="flex items-center gap-2">
          <div className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary text-white shadow-[0px_2px_4px_rgba(47,79,178,0.15)]">
            <LogoPawIcon size={17} />
          </div>
          <h1 className="text-[18px] font-bold tracking-[-0.45px] text-primary-dark">CATCHECK</h1>
        </div>
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.notifications);
            }}
            aria-label={t("nav.notifications")}
            className="flex size-11 items-center justify-center rounded-full text-text-secondary hover:bg-background-alt"
          >
            <BellIcon size={15} />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.settingsProfile);
            }}
            aria-label={t("nav.profile")}
            className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-dark text-white"
          >
            <AvatarPersonIcon size={13} />
          </button>
        </div>
      </div>
    </header>
  );
}

/**
 * Bottom nav mobile — khớp Figma node 1:2242. Nút "Quét" ở giữa là FAB tròn nổi lên, viền
 * trắng 4px, chấm vàng báo hiệu — KHÔNG phải tab thường như 4 nút còn lại.
 */
function BottomNav() {
  const { t } = useTranslation("common");
  const [home, history, scan, community, profile] = MOBILE_NAV_ITEMS;
  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-[var(--z-bottom-nav)] mx-auto flex w-full max-w-[480px] rounded-t-2xl bg-[rgba(255,255,255,0.95)] shadow-[0px_-4px_24px_0px_rgba(47,79,178,0.08)] backdrop-blur-md lg:hidden"
      aria-label={t("nav.mainLabel")}
    >
      <div className="flex h-16 w-full items-center gap-1 px-1">
        {[home, history].map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className="flex min-h-11 min-w-14 flex-1 flex-col items-center justify-center gap-0.5 py-1"
          >
            {({ isActive }) => (
              <>
                <item.icon size={20} className={isActive ? "text-primary" : "text-nav-inactive"} />
                <span className={cn("text-[11px] tracking-[0.4px]", isActive ? "text-primary" : "text-nav-inactive")}>
                  {t(item.labelKey)}
                </span>
                <span className={cn("size-1.5 rounded-full bg-secondary", !isActive && "opacity-0")} />
              </>
            )}
          </NavLink>
        ))}

        <div className="relative h-[50px] w-14 flex-1">
          <NavLink
            to={scan.to}
            aria-label={t(scan.labelKey)}
            className="absolute left-1/2 top-[-24px] flex -translate-x-1/2 flex-col items-center"
          >
            <span className="flex size-14 items-center justify-center rounded-full border-4 border-white bg-primary text-white shadow-[0px_8px_24px_-4px_rgba(47,79,178,0.35)]">
              <NavScanIcon size={22} />
              <span className="absolute right-[-2px] top-[-2px] size-3.5 rounded-full border-2 border-white bg-secondary" />
            </span>
            <span className="pt-1 text-[11px] tracking-[0.4px] text-primary">{t(scan.labelKey)}</span>
          </NavLink>
        </div>

        {[community, profile].map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            className="flex min-h-11 min-w-14 flex-1 flex-col items-center justify-center gap-0.5 py-1"
          >
            {({ isActive }) => (
              <>
                <item.icon size={20} className={isActive ? "text-primary" : "text-nav-inactive"} />
                <span className={cn("text-[11px] tracking-[0.4px]", isActive ? "text-primary" : "text-nav-inactive")}>
                  {t(item.labelKey)}
                </span>
                <span className={cn("size-1.5 rounded-full bg-secondary", !isActive && "opacity-0")} />
              </>
            )}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}

/**
 * Sidebar desktop — Figma `Web - 02` node 16:7500 "Aside", rộng đúng 288px.
 * Bố cục: logo (h 83) · thẻ chuyển mèo (h 72) · Nav 8 mục (mỗi mục 256×44, bước 48) · thẻ
 * khuyến mãi ghim đáy.
 */
function DesktopAside() {
  const { t } = useTranslation("common");
  const user = useSessionStore((s) => s.user);

  return (
    <aside
      className="sticky top-0 hidden h-dvh w-72 shrink-0 flex-col border-r border-border bg-surface lg:flex"
      aria-label={t("nav.mainLabel")}
    >
      <div className="flex h-[83px] items-center justify-center px-4">
        <img src={webLogo} alt={t("app.name")} className="h-[53px] w-40 object-contain" />
      </div>

      {/* Thẻ hồ sơ mèo đang theo dõi (16:7504) */}
      <div className="px-4 pb-2">
        <div className="flex h-14 items-center gap-2 rounded-xl bg-background-alt px-2">
          <span className="relative flex size-10 shrink-0 items-center justify-center rounded-full bg-chip-bg text-body font-bold text-primary-dark">
            {(user?.displayName ?? "?").slice(0, 1).toUpperCase()}
            <span className="absolute -bottom-0 -right-0 size-3 rounded-full border-2 border-surface bg-success" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-caption font-bold text-text-primary">
              {user?.displayName ?? t("app.name")}
            </span>
            <span className="block truncate text-[11px] leading-[14px] text-text-secondary">
              {t("webShell.switcherWatching")}
            </span>
          </span>
          <img src={iconChevron} alt="" className="h-[13.425px] w-[6.75px] shrink-0" />
        </div>
      </div>

      <nav className="flex flex-1 flex-col gap-1 overflow-y-auto px-4" aria-label={t("nav.mainLabel")}>
        {DESKTOP_NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.end}
            className={({ isActive }) =>
              cn(
                "flex h-11 items-center gap-3 rounded-xl px-4 text-caption font-semibold transition-colors",
                isActive
                  ? "bg-primary-dark text-white shadow-[0px_4px_6px_-1px_rgba(13,54,154,0.2)]"
                  : "text-text-secondary hover:bg-background-alt",
              )
            }
          >
            <NavMaskIcon src={item.icon} className="size-[18px]" />
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>

      {/* Thẻ khuyến mãi ghim đáy (16:7559) */}
      <div className="p-4">
        <div className="rounded-xl bg-secondary/25 p-4">
          <div className="flex items-center justify-between">
            <span className="text-[11px] font-bold tracking-[0.4px] text-secondary-text-on">
              {t("webShell.promoLabel")}
            </span>
            <img src={iconPromo} alt="" className="h-[15.75px] w-[16.5px]" />
          </div>
          <p className="pt-1 text-caption font-bold text-secondary-text-on">{t("webShell.promoTitle")}</p>
          <p className="text-[11px] leading-[14px] text-secondary-text-on/80">{t("webShell.promoNote")}</p>
        </div>
      </div>
    </aside>
  );
}

/** Header desktop — Figma node 16:7475, cao đúng 64px: ô tìm kiếm · pill trạng thái · chuông · user. */
function DesktopHeader() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const user = useSessionStore((s) => s.user);

  return (
    <header className="sticky top-0 z-[var(--z-sticky-header)] hidden h-16 w-full shrink-0 items-center gap-6 border-b border-border bg-surface px-6 lg:flex">
      <div className="relative flex h-10 max-w-[549px] flex-1 items-center">
        <img src={iconSearch} alt="" className="pointer-events-none absolute left-3 size-[15px]" />
        <input
          type="search"
          placeholder={t("webShell.searchPlaceholder")}
          aria-label={t("webShell.searchPlaceholder")}
          className="h-10 w-full rounded-xl bg-background-alt pl-10 pr-4 text-caption text-text-primary placeholder:text-text-tertiary focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]"
        />
      </div>

      <div className="ml-auto flex items-center gap-4">
        <span className="flex h-7 items-center gap-2 rounded-full bg-success-bg px-3">
          <span className="size-2.5 rounded-full bg-success" />
          <span className="text-[11px] font-semibold text-success-text">{t("webShell.statusPill")}</span>
        </span>

        <button
          type="button"
          onClick={() => {
            void navigate(ROUTE_PATTERNS.notifications);
          }}
          aria-label={t("nav.notifications")}
          className="relative flex size-10 items-center justify-center rounded-xl hover:bg-background-alt"
        >
          <img src={iconBell} alt="" className="h-[16.667px] w-[13.333px]" />
          <span className="absolute right-2 top-2 size-2 rounded-full bg-danger" />
        </button>

        {/* Trang chủ xem được ở chế độ khách — khi chưa đăng nhập thì hiện CTA đăng nhập
            thay vì ô hồ sơ (bấm vào sẽ bị RequireAuth đá về login, gây cụt luồng). */}
        {user ? (
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.settingsProfile);
            }}
            className="flex items-center gap-3 rounded-xl py-1 pl-2 hover:bg-background-alt"
          >
            <span className="text-right">
              <span className="block text-caption font-bold text-text-primary">{user.displayName}</span>
              <span className="block text-[11px] leading-[14px] text-text-secondary">{user.email}</span>
            </span>
            <span className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary-dark">
              <img src={iconAvatar} alt="" className="size-3" />
            </span>
          </button>
        ) : (
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.login);
            }}
            className="flex h-9 items-center justify-center rounded-xl bg-primary px-4 text-caption font-semibold text-white"
          >
            {t("dashboard.guestBanner.login")}
          </button>
        )}
      </div>
    </header>
  );
}

/**
 * Layout ứng dụng chính.
 *
 * - `< lg`: chrome mobile của Figma 390px — header dính trên + bottom nav có FAB "Quét".
 * - `>= lg`: chrome desktop của Figma `Web - 02` (1280px) — Aside 288px + Header 64px, vùng
 *   nội dung rộng 992px và các trang được `main` cấp sẵn padding 24px (khớp `Container`
 *   16:7088 → section rộng 944px). Trang con KHÔNG cần tự đặt padding ngang ở desktop.
 */
export function AppLayout() {
  return (
    <div className="flex min-h-dvh bg-background">
      <DesktopAside />
      <div className="flex min-w-0 flex-1 flex-col">
        <AppHeader />
        <DesktopHeader />
        <main className="flex-1 pb-[calc(64px+var(--space-4))] lg:mx-auto lg:w-full lg:max-w-[992px] lg:px-6 lg:py-6 lg:pb-6">
          <Outlet />
        </main>
      </div>
      <BottomNav />
    </div>
  );
}
