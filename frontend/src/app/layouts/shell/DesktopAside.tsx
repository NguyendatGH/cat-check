import { NavLink, useLocation } from "react-router";
import { isNavItemActive } from "./navActive";
import { useTranslation } from "react-i18next";
import { Sparkles } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { CatAvatar } from "@/entities/cat";
import { useSessionStore } from "@/entities/user";
import { useCatList } from "@/features/cat";
import { formatRemainingSeconds, useCreditBalance } from "@/features/credit";
import webLogo from "@/shared/assets/images/web-auth/logo.png";
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

const DESKTOP_NAV_EXTRA = [{ to: ROUTE_PATTERNS.assistant, icon: Sparkles, labelKey: "nav.assistant" }] as const;

const NAV_ITEM_BASE = "nav-item-animated group flex h-11 items-center gap-3 rounded-xl px-4 text-[14px] leading-5 cursor-pointer select-none";
const NAV_ACTIVE_CLASS = "bg-gradient-to-r from-primary-dark to-primary font-semibold text-white shadow-[0px_4px_14px_rgba(13,54,154,0.28),inset_0_1px_0_rgba(255,255,255,0.22)]";
const NAV_IDLE_CLASS = "font-medium text-text-secondary hover:bg-background-alt/80 hover:text-text-primary hover:translate-x-1";

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

function CatSwitcherCard() {
  const { t } = useTranslation("common");
  const user = useSessionStore((s) => s.user);
  const { data, isPending } = useCatList("ACTIVE", Boolean(user));
  const cats = data?.items ?? [];
  const cat = cats.find((c) => c.isPrimary) ?? cats.at(0);

  if (!user) return null;

  if (isPending) {
    return (
      <div className="px-4 pb-2.5">
        <div aria-hidden="true" className="h-14 animate-pulse rounded-2xl bg-background-alt/70" />
      </div>
    );
  }

  if (!cat) {
    return (
      <div className="sidebar-card-enter px-4 pb-2.5">
        <div className="rounded-2xl border border-border/40 bg-background-alt/60 p-1 shadow-[0_2px_8px_rgba(47,79,178,0.03)]">
          <NavLink
            to={ROUTE_PATTERNS.catNew}
            className="group flex h-[52px] items-center gap-2.5 rounded-xl bg-surface px-2.5 text-caption text-text-secondary transition-all duration-200 hover:bg-chip-bg/50 hover:shadow-xs"
          >
            <span className="flex size-9 shrink-0 items-center justify-center rounded-full bg-chip-bg text-h3 font-bold text-primary-dark transition-transform duration-200 group-hover:scale-105">
              +
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-bold text-text-primary">{t("webShell.switcherEmpty")}</span>
              <span className="block truncate text-[11px] leading-[14px]">{t("webShell.switcherEmptyCta")}</span>
            </span>
          </NavLink>
        </div>
      </div>
    );
  }

  const years = cat.ageMonths == null ? null : Math.round((cat.ageMonths / 12) * 10) / 10;

  return (
    <div className="sidebar-card-enter px-4 pb-2.5">
      <div className="rounded-2xl border border-border/40 bg-background-alt/60 p-1 shadow-[0_2px_8px_rgba(47,79,178,0.03)]">
        <NavLink
          to={ROUTE_PATTERNS.catsList}
          className="group flex h-[52px] items-center gap-2.5 rounded-xl bg-surface px-2.5 transition-all duration-200 hover:bg-surface/90 hover:shadow-xs"
        >
          <span className="relative flex size-9 shrink-0 items-center justify-center">
            <CatAvatar src={cat.avatarUrl} name={cat.name} size="md" className="size-9 ring-1 ring-border/20" />
            <span className="absolute bottom-0 right-0 size-2.5 rounded-full border-2 border-surface bg-success shadow-[0_0_6px_rgba(34,197,94,0.6)]" />
          </span>
          <span className="min-w-0 flex-1">
            <span className="block truncate text-caption font-bold text-text-primary group-hover:text-primary-dark transition-colors duration-200">
              {cat.breedName ? t("webShell.switcherTitle", { name: cat.name, breed: cat.breedName }) : cat.name}
            </span>
            <span className="block truncate text-[11px] leading-[14px] text-text-secondary">
              {years == null ? t("webShell.switcherWatching") : t("webShell.switcherMeta", { age: years })}
            </span>
          </span>
          <img
            src={iconChevron}
            alt=""
            className="h-[13.5px] w-[7px] shrink-0 opacity-60 transition-all duration-200 group-hover:opacity-100 group-hover:translate-x-0.5"
          />
        </NavLink>
      </div>
    </div>
  );
}

function CreditsCard() {
  const { t } = useTranslation("common");
  const { t: tCredit } = useTranslation("credit");
  const { data, isPending, isError } = useCreditBalance();

  if (isPending) {
    return <div aria-hidden="true" className="h-[104px] animate-pulse rounded-2xl bg-secondary/30" />;
  }

  const header = (
    <div className="flex items-center justify-between gap-2">
      <span className="text-overline font-bold tracking-[0.4px] text-secondary-text-on">
        {t("webShell.promoLabel")}
      </span>
      <img src={iconPromo} alt="" className="h-[15.75px] w-[16.5px] shrink-0" />
    </div>
  );

  const balance = isError ? undefined : data;
  const credits = balance?.availableBalance ?? 0;
  if (balance && credits > 0) {
    const nearest = balance.batches.find((b) => b.remainingAmount > 0);
    return (
      <div className="rounded-2xl border border-amber-200/70 bg-gradient-to-br from-amber-100/70 via-amber-50/40 to-yellow-100/60 p-1 shadow-[0_4px_16px_rgba(253,207,82,0.15)]">
        <NavLink
          to={ROUTE_PATTERNS.credits}
          className="block rounded-xl bg-gradient-to-br from-secondary via-secondary/95 to-secondary-light/90 p-3.5 transition-all duration-200 hover:shadow-xs active:scale-[0.98]"
        >
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
      </div>
    );
  }

  const trial = balance?.trialScansRemaining ?? 0;
  return (
    <div className="rounded-2xl border border-amber-200/70 bg-gradient-to-br from-amber-100/70 via-amber-50/40 to-yellow-100/60 p-1 shadow-[0_4px_16px_rgba(253,207,82,0.15)]">
      <div className="rounded-xl bg-gradient-to-br from-secondary via-secondary/95 to-secondary-light/90 p-3.5">
        {header}
        {balance ? (
          <p className="pt-1 text-body font-bold text-text-primary">
            {trial > 0 ? t("webShell.creditsTrial", { count: trial }) : t("webShell.promoTitle")}
          </p>
        ) : null}
        <p className="pt-0.5 text-small text-text-primary/80">{t("webShell.promoNote")}</p>
        <NavLink
          to={ROUTE_PATTERNS.creditsActivate}
          className="mt-3 flex min-h-10 items-center justify-center rounded-lg bg-surface px-3 text-caption font-bold text-primary-dark shadow-[0_2px_8px_rgba(13,54,154,0.12),inset_0_1px_0_rgba(255,255,255,0.8)] transition-all duration-200 hover:bg-white active:scale-[0.98]"
        >
          {t("webShell.promoCta")}
        </NavLink>
      </div>
    </div>
  );
}

export function DesktopAside() {
  const { t } = useTranslation("common");
  const signedIn = Boolean(useSessionStore((s) => s.user));
  const { pathname } = useLocation();

  return (
    <div className="hidden w-72 shrink-0 border-r border-border/50 bg-surface/95 backdrop-blur-xs lg:block">
      <div className="sticky top-0 flex h-dvh flex-col">
        <div className="flex h-[83px] shrink-0 items-center justify-center px-4">
          <NavLink
            to={ROUTE_PATTERNS.dashboard}
            className="group flex items-center justify-center rounded-xl p-1 transition-transform duration-200 active:scale-95"
            aria-label={t("app.name")}
          >
            <img src={webLogo} alt={t("app.name")} className="h-[53px] w-40 object-contain transition-opacity duration-200 group-hover:opacity-90" />
          </NavLink>
        </div>

        <CatSwitcherCard />

        <nav className="flex min-h-0 flex-1 flex-col gap-1 overflow-y-auto px-4 pt-1" aria-label={t("nav.mainLabel")}>
          {DESKTOP_NAV_ITEMS.map((item, index) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.end}
              data-motion="row"
              className={({ isActive }) => {
                const active = isNavItemActive(item.to, pathname, isActive);
                return cn(
                  NAV_ITEM_BASE,
                  "nav-stagger-enter",
                  active ? NAV_ACTIVE_CLASS : NAV_IDLE_CLASS,
                );
              }}
              style={{ animationDelay: `${index * 35}ms` }}
            >
              {({ isActive }) => {
                const active = isNavItemActive(item.to, pathname, isActive);
                return (
                  <>
                    <NavMaskIcon
                      src={item.icon}
                      className={cn(
                        "size-[18px] transition-transform duration-200",
                        active ? "scale-105" : "group-hover:scale-105",
                      )}
                    />
                    <span className="relative flex-1">
                      {t(item.labelKey)}
                    </span>
                    {active ? (
                      <span
                        className="size-1.5 rounded-full bg-secondary shadow-[0_0_8px_rgba(253,207,82,0.95)] animate-pop-spring"
                        aria-hidden="true"
                      />
                    ) : null}
                  </>
                );
              }}
            </NavLink>
          ))}
          {DESKTOP_NAV_EXTRA.map((item, index) => (
            <NavLink
              key={item.to}
              to={item.to}
              data-motion="row"
              className={({ isActive }) => {
                const active = isNavItemActive(item.to, pathname, isActive);
                return cn(
                  NAV_ITEM_BASE,
                  "nav-stagger-enter",
                  active ? NAV_ACTIVE_CLASS : NAV_IDLE_CLASS,
                );
              }}
              style={{ animationDelay: `${(DESKTOP_NAV_ITEMS.length + index) * 35}ms` }}
            >
              {({ isActive }) => {
                const active = isNavItemActive(item.to, pathname, isActive);
                return (
                  <>
                    <item.icon
                      size={18}
                      className={cn(
                        "shrink-0 transition-transform duration-200",
                        active ? "scale-105 text-secondary" : "group-hover:scale-105",
                      )}
                      aria-hidden="true"
                    />
                    <span className="relative flex-1">{t(item.labelKey)}</span>
                    {active ? (
                      <span
                        className="size-1.5 rounded-full bg-secondary shadow-[0_0_8px_rgba(253,207,82,0.95)] animate-pop-spring"
                        aria-hidden="true"
                      />
                    ) : null}
                  </>
                );
              }}
            </NavLink>
          ))}
        </nav>

        {signedIn ? (
          <div className="sidebar-card-enter shrink-0 p-4" style={{ animationDelay: "0.28s" }}>
            <CreditsCard />
          </div>
        ) : null}
      </div>
    </div>
  );
}
