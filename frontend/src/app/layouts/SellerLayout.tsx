import { Outlet, NavLink } from "react-router";
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

/** Layout khu vực người bán: sidebar ở desktop, thanh điều hướng gọn ở mobile. */
export function SellerLayout() {
  const { t } = useTranslation("seller");
  return (
    <div className="flex min-h-dvh flex-col bg-background md:flex-row">
      <nav
        aria-label={t("nav.label")}
        className="border-b border-border bg-surface md:flex md:w-56 md:shrink-0 md:flex-col md:gap-1 md:border-r md:border-b-0 md:p-4"
      >
        <p className="hidden px-3 pb-2 text-overline text-text-tertiary md:block">{t("nav.label")}</p>
        <ul className="flex gap-1 overflow-x-auto p-2 md:flex-col md:overflow-visible md:p-0">
          {SELLER_NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <li key={item.to} className="shrink-0">
                <NavLink
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    cn(
                      "flex min-h-[var(--touch-target-min)] items-center gap-2 rounded-lg px-3 py-2 text-caption font-semibold text-text-secondary hover:bg-background-alt md:text-body md:font-normal",
                      focusRing,
                      isActive && "bg-info text-primary",
                    )
                  }
                >
                  <Icon className="size-4 shrink-0" aria-hidden="true" />
                  {t(item.labelKey)}
                </NavLink>
              </li>
            );
          })}
        </ul>
      </nav>
      <main className="min-w-0 flex-1 p-4 md:p-6">
        <Outlet />
      </main>
    </div>
  );
}
