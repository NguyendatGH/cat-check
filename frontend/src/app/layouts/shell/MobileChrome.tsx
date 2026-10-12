import { NavLink, useLocation, useNavigate } from "react-router";
import { MapPinned, ShoppingBag, Sparkles, Ticket } from "lucide-react";
import { isNavItemActive } from "./navActive";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import {
  BellIcon,
  AvatarPersonIcon,
  NavHomeIcon,
  NavHistoryIcon,
  NavScanIcon,
  NavCommunityIcon,
  NavProfileIcon,
} from "@/shared/assets/icons/AppIcons";
import appLogo from "@/shared/assets/images/web-auth/logo.png";
import { useSessionStore } from "@/entities/user";
import { NotificationUnreadBadge } from "@/features/notification";
import { ROUTE_PATTERNS } from "../../router/routes";

/** Bottom nav mobile — GIỮ NGUYÊN 5 mục của Figma 390px (node 1:2242). */
const MOBILE_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.dashboard, icon: NavHomeIcon, labelKey: "nav.dashboard" },
  { to: ROUTE_PATTERNS.historyRedirect, icon: NavHistoryIcon, labelKey: "nav.history" },
  { to: ROUTE_PATTERNS.scan, icon: NavScanIcon, labelKey: "nav.scan" },
  { to: ROUTE_PATTERNS.community, icon: NavCommunityIcon, labelKey: "nav.community" },
  { to: ROUTE_PATTERNS.settingsProfile, icon: NavProfileIcon, labelKey: "nav.profile" },
] as const;

/**
 * Header mobile — khớp Figma "02. Trang chủ" node 1:2217 (khung 390px): logo tròn xanh +
 * "CATCHECK", chuông thông báo, avatar. Ẩn từ `lg` trở lên.
 */
export function MobileAppHeader() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const sessionUser = useSessionStore((s) => s.user);
  const signedIn = Boolean(sessionUser);
  return (
    <header className="sticky top-0 z-[var(--z-sticky-header)] w-full bg-[rgba(250,248,255,0.8)] shadow-[0px_1px_8px_0px_rgba(0,0,0,0.04)] backdrop-blur-md lg:hidden">
      <div className="flex h-14 items-center justify-between px-2">
        <div className="flex min-w-0 items-center gap-1.5">
          <img src={appLogo} alt={t("app.name")} className="h-10 w-[108px] object-contain object-left" />
        </div>
        <div className="flex shrink-0 items-center gap-0">
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.map);
            }}
            aria-label={t("nav.vetMap")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-colors hover:bg-background-alt hover:text-primary-dark"
          >
            <MapPinned size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.shop);
            }}
            aria-label={t("nav.shop")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-colors hover:bg-background-alt hover:text-primary-dark"
          >
            <ShoppingBag size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.credits : ROUTE_PATTERNS.login);
            }}
            aria-label={t("nav.credits")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-colors hover:bg-background-alt hover:text-primary-dark"
          >
            <Ticket size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.assistant : ROUTE_PATTERNS.login);
            }}
            aria-label={t("nav.assistant")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-colors hover:bg-background-alt hover:text-primary-dark"
          >
            <Sparkles size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.notifications);
            }}
            aria-label={t("nav.notifications")}
            className="relative flex size-9 items-center justify-center rounded-full text-text-secondary hover:bg-background-alt"
          >
            <BellIcon size={15} />
            <NotificationUnreadBadge enabled={signedIn} />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.settingsProfile : ROUTE_PATTERNS.login);
            }}
            aria-label={signedIn ? t("nav.profile") : t("dashboard.guestBanner.login")}
            className="flex size-9 shrink-0 items-center justify-center rounded-full"
          >
            <span className="flex size-8 items-center justify-center overflow-hidden rounded-full bg-primary-dark text-white">
              {sessionUser?.avatarUrl ? (
                <img src={sessionUser.avatarUrl} alt="" className="size-full object-cover" />
              ) : (
                <AvatarPersonIcon size={13} />
              )}
            </span>
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
export function BottomNav() {
  const { t } = useTranslation("common");
  const [home, history, scan, community, profile] = MOBILE_NAV_ITEMS;
  const { pathname } = useLocation();

  const tab = (item: (typeof MOBILE_NAV_ITEMS)[number]) => (
    <NavLink
      key={item.to}
      to={item.to}
      className="flex min-h-11 min-w-0 flex-1 flex-col items-center justify-center gap-0.5 py-1"
    >
      {({ isActive: routerActive }) => {
        const isActive = isNavItemActive(item.to, pathname, routerActive);
        return (
          <>
            <item.icon size={20} className={isActive ? "text-primary" : "text-nav-inactive"} />
            <span
              className={cn(
                "max-w-full truncate text-[11px] tracking-[0.4px]",
                isActive ? "font-semibold text-primary" : "text-nav-inactive",
              )}
            >
              {t(item.labelKey)}
            </span>
            <span className={cn("size-1.5 rounded-full bg-secondary", !isActive && "opacity-0")} />
          </>
        );
      }}
    </NavLink>
  );

  return (
    <nav
      className="fixed inset-x-0 bottom-0 z-[var(--z-bottom-nav)] mx-auto flex w-full max-w-[480px] rounded-t-2xl bg-[rgba(255,255,255,0.95)] pb-[env(safe-area-inset-bottom)] shadow-[0px_-4px_24px_0px_rgba(47,79,178,0.08)] backdrop-blur-md lg:hidden"
      aria-label={t("nav.mainLabel")}
    >
      <div className="flex h-16 w-full items-center gap-1 px-1">
        {tab(home)}
        {tab(history)}

        <div className="relative h-[50px] min-w-0 flex-1">
          <NavLink
            to={scan.to}
            aria-label={t(scan.labelKey)}
            data-motion="none"
            className="absolute left-1/2 top-[-24px] flex -translate-x-1/2 flex-col items-center"
          >
            <span className="relative flex size-14 items-center justify-center rounded-full border-4 border-white bg-primary text-white shadow-[0px_8px_24px_-4px_rgba(47,79,178,0.35)]">
              <NavScanIcon size={22} />
              <span className="absolute right-[-2px] top-[-2px] size-3.5 rounded-full border-2 border-white bg-secondary" />
            </span>
            <span className="pt-1 text-[11px] font-semibold tracking-[0.4px] text-primary">{t(scan.labelKey)}</span>
          </NavLink>
        </div>

        {tab(community)}
        {tab(profile)}
      </div>
    </nav>
  );
}
