import { Outlet, NavLink } from "react-router";
import { useTranslation } from "react-i18next";
import { cn } from "@/shared/lib/cn";
import { ROUTE_PATTERNS } from "../router/routes";

const ADMIN_NAV_ITEMS = [
  { to: ROUTE_PATTERNS.adminDashboard, labelKey: "nav.dashboard" },
  { to: ROUTE_PATTERNS.adminUsers, labelKey: "nav.users" },
  { to: ROUTE_PATTERNS.adminActivationCodes, labelKey: "nav.activationCodes" },
  { to: ROUTE_PATTERNS.adminPackages, labelKey: "nav.packages" },
  { to: ROUTE_PATTERNS.adminProducts, labelKey: "nav.products" },
  { to: ROUTE_PATTERNS.adminPhColorChart, labelKey: "nav.phColorChart" },
  { to: ROUTE_PATTERNS.adminContent, labelKey: "nav.content" },
  { to: ROUTE_PATTERNS.adminAuditLog, labelKey: "nav.auditLog" },
] as const;

/** Layout khu vực quản trị — sidebar cố định, dùng cho toàn bộ /admin/** (trừ /admin/setup-2fa). */
export function AdminLayout() {
  const { t } = useTranslation("admin");
  return (
    <div className="flex min-h-dvh bg-background">
      <nav
        className="flex w-56 shrink-0 flex-col gap-1 border-r border-border bg-surface p-4"
        aria-label={t("nav.label")}
      >
        <p className="mb-2 px-3 text-overline text-text-tertiary">{t("nav.label")}</p>
        {ADMIN_NAV_ITEMS.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === ROUTE_PATTERNS.adminDashboard}
            className={({ isActive }) =>
              cn(
                "rounded-lg px-3 py-2 text-body text-text-secondary hover:bg-background-alt",
                isActive && "bg-info text-primary",
              )
            }
          >
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>
      <main className="flex-1 p-6">
        <Outlet />
      </main>
    </div>
  );
}
