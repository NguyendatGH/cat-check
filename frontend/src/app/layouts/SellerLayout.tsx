import { Outlet, NavLink, useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import { FlaskConical, LayoutDashboard, Lightbulb, Package, type LucideIcon } from "lucide-react";
import { cn } from "@/shared/lib/cn";
import { ROUTE_PATTERNS } from "../router/routes";

const SELLER_NAV_ITEMS: readonly { to: string; labelKey: string; icon: LucideIcon; end: boolean }[] = [
  { to: ROUTE_PATTERNS.sellerDashboard, labelKey: "nav.dashboard", icon: LayoutDashboard, end: true },
  { to: ROUTE_PATTERNS.sellerProducts, labelKey: "nav.products", icon: Package, end: false },
  { to: ROUTE_PATTERNS.sellerColorChart, labelKey: "nav.colorChart", icon: FlaskConical, end: false },
  { to: ROUTE_PATTERNS.sellerContent, labelKey: "nav.content", icon: Lightbulb, end: false },
];

const focusRing =
  "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]";

export function SellerLayout() {
  const { t } = useTranslation("seller");
  const { pathname } = useLocation();

  return (
    <div className="flex min-h-dvh flex-col bg-background md:flex-row">
      <nav
        aria-label={t("nav.label")}
        className="header-glass sticky top-0 z-[var(--z-sticky-header)] border-b border-border/60 md:static md:flex md:w-60 md:shrink-0 md:flex-col md:gap-1.5 md:border-r md:border-b-0 md:bg-surface md:p-4"
      >
        <div className="hidden px-3 py-2 md:block">
          <p className="text-overline font-bold tracking-wider text-text-tertiary">{t("nav.label")}</p>
        </div>
        <ul className="flex gap-1 overflow-x-auto p-2 md:flex-col md:overflow-visible md:p-0">
          {SELLER_NAV_ITEMS.map((item, index) => {
            const Icon = item.icon;
            const isMatch = item.end ? pathname === item.to : pathname.startsWith(item.to);
            return (
              <li key={item.to} className="shrink-0 md:w-full">
                <NavLink
                  to={item.to}
                  end={item.end}
                  style={{ animationDelay: `${index * 40}ms` }}
                  className={({ isActive }) =>
                    cn(
                      "tab-pill-animated group flex min-h-[var(--touch-target-min)] items-center gap-2.5 rounded-xl px-3.5 py-2.5 text-caption font-semibold transition-all duration-200 md:text-body md:font-medium",
                      focusRing,
                      isActive
                        ? "bg-primary text-white shadow-[0_4px_12px_rgba(47,79,178,0.25)]"
                        : "text-text-secondary hover:bg-background-alt hover:text-text-primary md:hover:translate-x-1",
                    )
                  }
                >
                  <Icon
                    className={cn(
                      "size-4 shrink-0 transition-transform duration-200",
                      isMatch ? "scale-110" : "group-hover:scale-105",
                    )}
                    aria-hidden="true"
                  />
                  <span className="flex-1 truncate">{t(item.labelKey)}</span>
                  {isMatch ? (
                    <span
                      className="size-1.5 rounded-full bg-secondary shadow-[0_0_6px_rgba(255,200,50,0.8)] animate-pop-spring hidden md:inline-block"
                      aria-hidden="true"
                    />
                  ) : null}
                </NavLink>
              </li>
            );
          })}
        </ul>
      </nav>
      <main className="min-w-0 flex-1 p-4 md:p-6 lg:p-8 animate-reveal">
        <Outlet />
      </main>
    </div>
  );
}
