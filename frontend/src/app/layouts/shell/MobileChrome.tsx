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

const MOBILE_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.dashboard, icon: NavHomeIcon, labelKey: "nav.dashboard" },
  { to: ROUTE_PATTERNS.historyRedirect, icon: NavHistoryIcon, labelKey: "nav.history" },
  { to: ROUTE_PATTERNS.scan, icon: NavScanIcon, labelKey: "nav.scan" },
  { to: ROUTE_PATTERNS.community, icon: NavCommunityIcon, labelKey: "nav.community" },
  { to: ROUTE_PATTERNS.settingsProfile, icon: NavProfileIcon, labelKey: "nav.profile" },
] as const;

export function MobileAppHeader() {
  const { t } = useTranslation("common");
  const navigate = useNavigate();
  const sessionUser = useSessionStore((s) => s.user);
  const signedIn = Boolean(sessionUser);
  return (
    <header className="header-glass sticky top-0 z-[var(--z-sticky-header)] w-full lg:hidden">
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
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-all duration-200 hover:bg-background-alt hover:text-primary-dark active:scale-95"
          >
            <MapPinned size={16} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.shop);
            }}
            aria-label={t("nav.shop")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-all duration-200 hover:bg-background-alt hover:text-primary-dark active:scale-95"
          >
            <ShoppingBag size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.credits : ROUTE_PATTERNS.login);
            }}
            aria-label={t("nav.credits")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-all duration-200 hover:bg-background-alt hover:text-primary-dark active:scale-95"
          >
            <Ticket size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(signedIn ? ROUTE_PATTERNS.assistant : ROUTE_PATTERNS.login);
            }}
            aria-label={t("nav.assistant")}
            className="flex size-9 items-center justify-center rounded-full text-text-secondary transition-all duration-200 hover:bg-background-alt hover:text-primary-dark active:scale-95"
          >
            <Sparkles size={17} aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => {
              void navigate(ROUTE_PATTERNS.notifications);
            }}
            aria-label={t("nav.notifications")}
            className="relative flex size-9 items-center justify-center rounded-full text-text-secondary transition-all duration-200 hover:bg-background-alt active:scale-95"
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
            className="flex size-9 shrink-0 items-center justify-center rounded-full transition-transform duration-200 active:scale-95"
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

export function BottomNav() {
  const { t } = useTranslation("common");
  const [home, history, scan, community, profile] = MOBILE_NAV_ITEMS;
  const { pathname } = useLocation();

  const tab = (item: (typeof MOBILE_NAV_ITEMS)[number]) => (
    <NavLink
      key={item.to}
      to={item.to}
      className={({ isActive: routerActive }) => {
        const isActive = isNavItemActive(item.to, pathname, routerActive);
        return cn(
          "btm-nav-tab relative flex min-h-11 min-w-0 flex-1 flex-col items-center justify-center gap-0.5 py-1.5 transition-all duration-200 active:scale-95",
          isActive && "btm-nav-tab-active",
        );
      }}
    >
      {({ isActive: routerActive }) => {
        const isActive = isNavItemActive(item.to, pathname, routerActive);
        return (
          <>
            {isActive ? <span className="btm-nav-tab-pill" aria-hidden="true" /> : null}
            <span
              className={cn(
                "btm-nav-tab-icon relative z-10",
                isActive ? "scale-110 -translate-y-0.5" : "",
              )}
            >
              <item.icon size={20} className={isActive ? "text-primary-dark" : "text-nav-inactive"} />
            </span>
            <span
              className={cn(
                "btm-nav-tab-label relative z-10 max-w-full truncate text-[11px] tracking-[0.3px]",
                isActive ? "font-bold text-primary-dark" : "font-medium text-nav-inactive",
              )}
            >
              {t(item.labelKey)}
            </span>
            <span
              className={cn(
                "nav-active-dot relative z-10 size-1.5 rounded-full bg-secondary shadow-[0_0_8px_rgba(253,207,82,0.9)]",
                isActive ? "opacity-100 scale-100" : "opacity-0 scale-0",
              )}
              aria-hidden="true"
            />
          </>
        );
      }}
    </NavLink>
  );

  return (
    <nav
      className="bottom-nav-glass fixed inset-x-0 bottom-0 z-[var(--z-bottom-nav)] mx-auto flex w-full max-w-[480px] rounded-t-2xl pb-[env(safe-area-inset-bottom)] lg:hidden"
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
            className="group absolute left-1/2 top-[-24px] flex -translate-x-1/2 flex-col items-center transition-transform duration-200 active:scale-95"
          >
            <span className="fab-scan-pulse relative flex size-14 items-center justify-center rounded-full border-4 border-white bg-gradient-to-tr from-primary-dark to-primary text-white shadow-[0_8px_20px_rgba(47,79,178,0.32)] transition-transform duration-200 group-hover:scale-105">
              <NavScanIcon size={22} />
              <span className="absolute right-[-2px] top-[-2px] size-3.5 rounded-full border-2 border-white bg-secondary shadow-[0_0_6px_rgba(253,207,82,0.8)]" />
            </span>
            <span className="pt-1 text-[11px] font-bold tracking-[0.4px] text-primary-dark">{t(scan.labelKey)}</span>
          </NavLink>
        </div>

        {tab(community)}
        {tab(profile)}
      </div>
    </nav>
  );
}
