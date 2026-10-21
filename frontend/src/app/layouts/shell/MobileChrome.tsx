import { useEffect, useState } from "react";
import { Link, NavLink, useLocation, useNavigate } from "react-router";
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
import { ShineLogo } from "@/shared/ui";
import appLogo from "@/shared/assets/images/web-auth/logo.png";
import { useHomePath, useSessionStore } from "@/entities/user";
import { NotificationUnreadBadge } from "@/features/notification";
import { ROUTE_PATTERNS } from "../../router/routes";

const MOBILE_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.dashboard, icon: NavHomeIcon, labelKey: "nav.dashboard", tour: "tab-dashboard" },
  { to: ROUTE_PATTERNS.historyRedirect, icon: NavHistoryIcon, labelKey: "nav.history", tour: "tab-history" },
  { to: ROUTE_PATTERNS.scan, icon: NavScanIcon, labelKey: "nav.scan", tour: "tab-scan" },
  { to: ROUTE_PATTERNS.community, icon: NavCommunityIcon, labelKey: "nav.community", tour: "tab-community" },
  { to: ROUTE_PATTERNS.settingsProfile, icon: NavProfileIcon, labelKey: "nav.profile", tour: "tab-profile" },
] as const;

const ICON_BTN =
  "relative flex size-11 shrink-0 items-center justify-center rounded-full text-text-primary transition-colors duration-200 hover:bg-background-alt active:bg-primary/10";

// Pages that scroll less than this keep the row; hiding it would gain almost nothing.
const COLLAPSE_MIN_SCROLL = 160;

// Collapse the shortcut row once the user scrolls down; bring it back on scroll up.
// The header is clipped, not resized (see .mobile-header), so toggling never moves scrollY itself.
// Upward moves while pinned at the bottom (overscroll bounce, content shrinking) are not a user
// scroll-up, so they are ignored — otherwise the header flickers open/closed.
export function useHeaderCollapsed() {
  const [collapsed, setCollapsed] = useState(false);
  useEffect(() => {
    let lastY = window.scrollY;
    let frame = 0;
    const onScroll = () => {
      if (frame) return;
      frame = window.requestAnimationFrame(() => {
        frame = 0;
        const y = window.scrollY;
        if (Math.abs(y - lastY) < 8) return;
        const maxScroll = document.documentElement.scrollHeight - window.innerHeight;
        const atBottom = y >= maxScroll - 2;
        if (y < lastY && atBottom) {
          lastY = y;
          return;
        }
        const scrollingDown = y > 64 && y > lastY;
        setCollapsed((prev) => scrollingDown && (prev || maxScroll > COLLAPSE_MIN_SCROLL));
        lastY = y;
      });
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (frame) window.cancelAnimationFrame(frame);
    };
  }, []);
  return collapsed;
}

export function MobileAppHeader() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const homePath = useHomePath();
  const sessionUser = useSessionStore((s) => s.user);
  const signedIn = Boolean(sessionUser);
  const collapsed = useHeaderCollapsed();

  const shortcuts = [
    { key: "map", icon: MapPinned, label: t("nav.vetMap"), to: ROUTE_PATTERNS.map },
    { key: "shop", icon: ShoppingBag, label: t("nav.shop"), to: ROUTE_PATTERNS.shop },
    { key: "credits", icon: Ticket, label: t("nav.credits"), to: signedIn ? ROUTE_PATTERNS.credits : ROUTE_PATTERNS.login },
    { key: "assistant", icon: Sparkles, label: t("nav.assistant"), to: signedIn ? ROUTE_PATTERNS.assistant : ROUTE_PATTERNS.login },
  ];

  return (
    <header
      className="header-glass mobile-header sticky top-0 z-[var(--z-sticky-header)] w-full lg:hidden"
      data-collapsed={collapsed}
    >
      <div className="flex h-16 items-center justify-between gap-2 pl-4 pr-2">
        <Link to={homePath} className="inline-flex min-w-0 items-center" aria-label={t("app.name")}>
          <ShineLogo src={appLogo} alt={t("app.name")} align="left" className="h-11 w-[120px] object-contain object-left" />
        </Link>
        <div className="flex shrink-0 items-center gap-1">
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.notifications);
            }}
            aria-label={t("nav.notifications")}
            data-tour="notifications"
            className={ICON_BTN}
          >
            <BellIcon size={22} />
            <NotificationUnreadBadge enabled={signedIn} />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.settingsProfile : ROUTE_PATTERNS.login);
            }}
            aria-label={signedIn ? t("nav.profile") : t("dashboard.guestBanner.login")}
            className={ICON_BTN}
          >
            <span className="flex size-9 items-center justify-center overflow-hidden rounded-full bg-primary-dark text-white ring-2 ring-primary/15">
              {sessionUser?.avatarUrl ? (
                <img src={sessionUser.avatarUrl} alt="" className="size-full object-cover" />
              ) : (
                <AvatarPersonIcon size={16} />
              )}
            </span>
          </button>
        </div>
      </div>
      <nav aria-label={t("nav.quickLabel")} className="header-shortcuts">
        <ul className="no-scrollbar flex gap-2 overflow-x-auto px-4 pb-2.5">
          {shortcuts.map((item) => (
            <li key={item.key} className="shrink-0">
              <NavLink
                to={item.to}
                data-tour={`shortcut-${item.key}`}
                tabIndex={collapsed ? -1 : undefined}
                className={({ isActive }) =>
                  cn(
                    "flex h-10 items-center gap-1.5 rounded-full border px-3.5 text-[13px] font-semibold transition-colors duration-200",
                    isActive
                      ? "border-primary-dark bg-primary-dark text-white"
                      : "border-border bg-surface text-text-primary hover:border-primary/40 active:bg-primary/10",
                  )
                }
              >
                <item.icon size={17} aria-hidden="true" />
                {item.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
    </header>
  );
}

export function BottomNav() {
  const { t } = useTranslation("common");
  const [home, history, scan, community, profile] = MOBILE_NAV_ITEMS;
  const { pathname } = useLocation();
  // Staff bị chặn ở /dashboard (RequireNonAdminRole): tab "Trang chủ" đưa họ về cổng quản trị.
  const homePath = useHomePath();

  const tab = (item: (typeof MOBILE_NAV_ITEMS)[number]) => (
    <NavLink
      key={item.to}
      to={item.to === ROUTE_PATTERNS.dashboard ? homePath : item.to}
      data-motion="none"
      data-tour={item.tour}
      className={({ isActive: routerActive }) =>
        cn("btm-tab", isNavItemActive(item.to, pathname, routerActive) && "btm-tab-active")
      }
    >
      <span className="btm-tab-indicator" aria-hidden="true">
        <item.icon size={22} />
      </span>
      <span className="btm-tab-label">{t(item.labelKey)}</span>
    </NavLink>
  );

  return (
    <nav
      className="bottom-nav-glass fixed inset-x-0 bottom-0 z-[var(--z-bottom-nav)] mx-auto w-full max-w-[480px] border-t border-border/60 pb-[env(safe-area-inset-bottom)] lg:hidden"
      aria-label={t("nav.mainLabel")}
    >
      <div className="grid h-[72px] grid-cols-5 items-stretch px-1">
        {tab(home)}
        {tab(history)}
        <NavLink
          to={scan.to}
          data-motion="none"
          data-tour={scan.tour}
          className={({ isActive: routerActive }) =>
            cn("btm-tab btm-tab-scan", isNavItemActive(scan.to, pathname, routerActive) && "btm-tab-active")
          }
        >
          <span className="btm-scan-btn" aria-hidden="true">
            <NavScanIcon size={26} />
          </span>
          <span className="btm-tab-label">{t(scan.labelKey)}</span>
        </NavLink>
        {tab(community)}
        {tab(profile)}
      </div>
    </nav>
  );
}
