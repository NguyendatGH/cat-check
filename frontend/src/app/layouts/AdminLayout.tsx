import { useCallback, useId, useMemo, useState } from "react";
import { Link, Outlet, useLocation } from "react-router";
import { useTranslation } from "react-i18next";
import {
  Archive,
  ArrowLeft,
  BellRing,
  BookOpenText,
  CalendarClock,
  FileCheck2,
  Flag,
  History,
  Inbox,
  KeyRound,
  LayoutDashboard,
  type LucideIcon,
  Megaphone,
  Menu,
  Package,
  Palette,
  ScanLine,
  ScrollText,
  Send,
  Settings,
  ShieldAlert,
  ShieldCheck,
  ShoppingBag,
  UserCog,
  Users,
  LogOut,
} from "lucide-react";
import { Sheet, SheetContent, SheetTitle, SheetTrigger } from "@/shared/ui";
import { cn } from "@/shared/lib/cn";
import { useSessionStore } from "@/entities/user";
import {
  AdminAccessProvider,
  computeCapabilities,
  filterNavGroups,
  findActiveNavItem,
  isNavItemActive,
  type AdminCapabilities,
  type AdminNavGroup,
  type AdminNavItemBase,
  type RoleCheck,
} from "@/features/admin";
import { ForbiddenPage } from "@/pages/system/ForbiddenPage";
import { ROUTE_PATTERNS } from "../router/routes";
import { ROLES, hasAnyRole } from "@/shared/auth/roles";

interface AdminNavItem extends AdminNavItemBase {
  labelKey: string;
  icon: LucideIcon;
}

/**
 * Menu quản trị theo nhóm. Mỗi mục gắn đúng bộ vai trò của API mà trang đó gọi
 * (`shared/auth/roles.ts`), nên người dùng không thấy mục mà server sẽ trả 403.
 * "Sự cố" và "Phiên" cùng trỏ `/admin/security`, phân biệt bằng `?tab=`.
 */
const ADMIN_NAV: readonly AdminNavGroup<AdminNavItem>[] = [
  {
    id: "overview",
    labelKey: "nav.groups.overview",
    items: [
      {
        id: "dashboard",
        to: ROUTE_PATTERNS.adminDashboard,
        end: true,
        labelKey: "nav.dashboard",
        icon: LayoutDashboard,
        roles: ROLES.dashboard,
      },
    ],
  },
  {
    id: "people",
    labelKey: "nav.groups.people",
    items: [
      { id: "users", to: ROUTE_PATTERNS.adminUsers, labelKey: "nav.users", icon: Users, roles: ROLES.users },
      { id: "staff", to: ROUTE_PATTERNS.adminStaff, labelKey: "nav.staff", icon: UserCog, roles: ROLES.staff },
      { id: "scans", to: ROUTE_PATTERNS.adminScans, labelKey: "nav.scans", icon: ScanLine, roles: ROLES.scans },
      {
        id: "consents",
        to: ROUTE_PATTERNS.adminPrivacyConsents,
        labelKey: "nav.consents",
        icon: FileCheck2,
        roles: ROLES.consentsRead,
      },
    ],
  },
  {
    id: "billing",
    labelKey: "nav.groups.billing",
    items: [
      {
        id: "packages",
        to: ROUTE_PATTERNS.adminPackages,
        labelKey: "nav.packages",
        icon: Package,
        roles: ROLES.packagesRead,
      },
      {
        id: "activationCodes",
        to: ROUTE_PATTERNS.adminActivationCodes,
        labelKey: "nav.activationCodes",
        icon: KeyRound,
        roles: ROLES.activationCodes,
      },
    ],
  },
  {
    id: "catalog",
    labelKey: "nav.groups.catalog",
    items: [
      {
        id: "products",
        to: ROUTE_PATTERNS.adminProducts,
        labelKey: "nav.products",
        icon: ShoppingBag,
        roles: ROLES.products,
      },
      {
        id: "phColorChart",
        to: ROUTE_PATTERNS.adminPhColorChart,
        labelKey: "nav.phColorChart",
        icon: Palette,
        roles: ROLES.colorChart,
      },
      {
        id: "content",
        to: ROUTE_PATTERNS.adminContent,
        labelKey: "nav.content",
        icon: BookOpenText,
        roles: ROLES.careTipsWrite,
      },
      {
        id: "monitoringRules",
        to: ROUTE_PATTERNS.adminMonitoringRules,
        labelKey: "nav.monitoringRules",
        icon: BellRing,
        roles: ROLES.monitoringRulesRead,
      },
    ],
  },
  {
    id: "community",
    labelKey: "nav.groups.community",
    items: [
      {
        id: "communityReports",
        to: ROUTE_PATTERNS.adminCommunityReports,
        labelKey: "nav.communityReports",
        icon: Flag,
        roles: ROLES.communityModeration,
      },
    ],
  },
  {
    id: "privacy",
    labelKey: "nav.groups.privacy",
    items: [
      {
        id: "privacyRequests",
        to: ROUTE_PATTERNS.adminPrivacyRequests,
        labelKey: "nav.privacyRequests",
        icon: Inbox,
        roles: ROLES.privacyRequests,
      },
      {
        id: "retention",
        to: ROUTE_PATTERNS.adminPrivacyRetention,
        labelKey: "nav.retention",
        icon: Archive,
        roles: ROLES.retention,
      },
      {
        id: "policyVersions",
        to: ROUTE_PATTERNS.adminPolicyVersions,
        labelKey: "nav.policyVersions",
        icon: ScrollText,
        roles: ROLES.policyVersionsRead,
      },
      {
        id: "incidents",
        to: ROUTE_PATTERNS.adminSecurity,
        tab: "incidents",
        defaultTab: true,
        labelKey: "nav.incidents",
        icon: ShieldAlert,
        roles: ROLES.securityIncidentsRead,
      },
      {
        id: "sessions",
        to: ROUTE_PATTERNS.adminSecurity,
        tab: "sessions",
        labelKey: "nav.sessions",
        icon: LogOut,
        roles: ROLES.securitySessionsPurge,
      },
    ],
  },
  {
    id: "ops",
    labelKey: "nav.groups.ops",
    items: [
      { id: "jobs", to: ROUTE_PATTERNS.adminJobs, labelKey: "nav.jobs", icon: CalendarClock, roles: ROLES.jobs },
      {
        id: "outbox",
        to: ROUTE_PATTERNS.adminNotificationsOutbox,
        labelKey: "nav.outbox",
        icon: Send,
        roles: ROLES.outbox,
      },
      {
        id: "broadcast",
        to: ROUTE_PATTERNS.adminBroadcast,
        labelKey: "nav.broadcast",
        icon: Megaphone,
        roles: ROLES.broadcast,
      },
      {
        id: "auditLog",
        to: ROUTE_PATTERNS.adminAuditLog,
        labelKey: "nav.auditLog",
        icon: History,
        roles: ROLES.auditLog,
      },
      {
        id: "settings",
        to: ROUTE_PATTERNS.adminMeSecurity,
        labelKey: "nav.settings",
        icon: Settings,
        roles: ROLES.adminPortal,
      },
    ],
  },
];

function hrefOf(item: AdminNavItem): string {
  return item.tab === undefined ? item.to : `${item.to}?tab=${item.tab}`;
}

interface AdminNavListProps {
  groups: readonly AdminNavGroup<AdminNavItem>[];
  location: { pathname: string; search: string };
  /** Menu trong Sheet trên di động: vùng chạm 44px và đóng Sheet khi chọn. */
  touch?: boolean;
  onNavigate?: () => void;
}

function AdminNavList({ groups, location, touch = false, onNavigate }: AdminNavListProps) {
  const { t } = useTranslation("admin");
  const baseId = useId();
  return (
    <div className="flex flex-col gap-1">
      {groups.map((group) => {
        const headingId = `${baseId}-${group.id}`;
        return (
          <div key={group.id} role="group" aria-labelledby={headingId}>
            <p id={headingId} className="px-3 pb-1 pt-3 text-overline uppercase tracking-wide text-text-tertiary">
              {t(group.labelKey)}
            </p>
            <ul className="flex flex-col gap-0.5">
              {group.items.map((item) => {
                const active = isNavItemActive(item, location);
                const Icon = item.icon;
                return (
                  <li key={item.id}>
                    <Link
                      to={hrefOf(item)}
                      aria-current={active ? "page" : undefined}
                      onClick={onNavigate}
                      className={cn(
                        "relative flex items-center gap-2.5 rounded-md px-3 text-caption text-text-secondary",
                        "transition-colors duration-[var(--duration-fast)] motion-reduce:transition-none",
                        "hover:bg-background-alt hover:text-text-primary",
                        "focus-visible:outline focus-visible:outline-[var(--focus-ring-width)] focus-visible:outline-offset-[var(--focus-ring-offset)] focus-visible:outline-[var(--focus-ring-color)]",
                        touch ? "min-h-11" : "min-h-9",
                        active && "bg-info font-semibold text-primary-dark hover:bg-info hover:text-primary-dark",
                      )}
                    >
                      {active ? (
                        <span
                          className="absolute inset-y-1.5 left-0 w-0.5 rounded-full bg-primary-dark"
                          aria-hidden="true"
                        />
                      ) : null}
                      <Icon size={16} aria-hidden="true" className="shrink-0" />
                      <span className="truncate">{t(item.labelKey)}</span>
                    </Link>
                  </li>
                );
              })}
            </ul>
          </div>
        );
      })}
    </div>
  );
}

/**
 * Layout khu vực quản trị cho toàn bộ /admin/** (trừ /admin/setup-2fa).
 *
 * - Chỉ người có một trong `ROLES.adminPortal` được vào; còn lại thấy trang 403 tại chỗ.
 * - Menu nhóm, mỗi mục chỉ hiện khi người dùng có vai trò tương ứng.
 * - Desktop: sidebar dính; di động: menu thu gọn trong `Sheet`.
 * - Tính sẵn bảng khả năng từ `ROLES` và đặt vào `AdminAccessProvider` để trang con ẩn/hiện
 *   nút ghi mà không phải import `shared/auth/roles.ts` (ESLint `boundaries` cấm).
 */
export function AdminLayout() {
  const { t } = useTranslation("admin");
  const location = useLocation();
  const roles = useSessionStore((state) => state.user?.roles);
  const [menuOpen, setMenuOpen] = useState(false);

  const allows = useCallback<RoleCheck>((allowed) => hasAnyRole(roles, allowed), [roles]);
  const capabilities: AdminCapabilities = useMemo(() => computeCapabilities(ROLES, allows), [allows]);
  const groups = useMemo(() => filterNavGroups(ADMIN_NAV, allows), [allows]);
  const activeItem = findActiveNavItem(groups, location);

  if (!allows(ROLES.adminPortal)) {
    return (
      <main className="min-h-dvh bg-background p-6">
        <ForbiddenPage />
      </main>
    );
  }

  return (
    <div className="flex min-h-dvh bg-background">
      <a
        href="#admin-main"
        className="sr-only z-[var(--z-toast)] rounded-md bg-surface px-3 py-2 text-caption font-semibold text-primary-dark focus:not-sr-only focus:fixed focus:left-3 focus:top-3"
      >
        {t("nav.skipToContent")}
      </a>

      <aside className="sticky top-0 hidden h-dvh w-60 shrink-0 flex-col border-r border-border bg-surface lg:flex">
        <div className="flex items-center gap-2.5 border-b border-border px-4 py-3">
          <span
            className="flex size-8 items-center justify-center rounded-lg bg-primary-dark text-white"
            aria-hidden="true"
          >
            <ShieldCheck size={16} />
          </span>
          <div className="min-w-0">
            <p className="truncate text-caption font-bold text-text-primary">{t("nav.brand")}</p>
            <p className="truncate text-small text-text-tertiary">{t("nav.label")}</p>
          </div>
        </div>
        <nav aria-label={t("nav.label")} className="flex-1 overflow-y-auto px-2 pb-3">
          <AdminNavList groups={groups} location={location} />
        </nav>
        <div className="border-t border-border p-2">
          <Link
            to={ROUTE_PATTERNS.dashboard}
            className="flex min-h-9 items-center gap-2.5 rounded-md px-3 text-caption text-text-secondary hover:bg-background-alt hover:text-text-primary"
          >
            <ArrowLeft size={16} aria-hidden="true" />
            {t("nav.backToApp")}
          </Link>
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-[var(--z-sticky-header)] flex items-center gap-2 border-b border-border bg-surface px-2 py-1.5 lg:hidden">
          <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
            <SheetTrigger asChild>
              <button
                type="button"
                aria-label={t("nav.openMenu")}
                className="flex size-11 items-center justify-center rounded-lg text-text-secondary hover:bg-background-alt"
              >
                <Menu size={20} aria-hidden="true" />
              </button>
            </SheetTrigger>
            <SheetContent aria-describedby={undefined} className="px-3 pb-6 pt-2">
              <SheetTitle className="px-3 text-body">{t("nav.label")}</SheetTitle>
              <nav aria-label={t("nav.label")}>
                <AdminNavList
                  groups={groups}
                  location={location}
                  touch
                  onNavigate={() => {
                    setMenuOpen(false);
                  }}
                />
              </nav>
              <Link
                to={ROUTE_PATTERNS.dashboard}
                className="mt-3 flex min-h-11 items-center gap-2.5 rounded-md border-t border-border px-3 pt-2 text-caption text-text-secondary"
              >
                <ArrowLeft size={16} aria-hidden="true" />
                {t("nav.backToApp")}
              </Link>
            </SheetContent>
          </Sheet>
          <p className="min-w-0 truncate text-caption font-semibold text-text-primary">
            {activeItem !== undefined ? t(activeItem.labelKey) : t("nav.label")}
          </p>
        </header>

        <main id="admin-main" tabIndex={-1} className="flex-1 px-3 py-4 sm:px-4 lg:px-6 lg:py-5">
          <AdminAccessProvider capabilities={capabilities}>
            <Outlet />
          </AdminAccessProvider>
        </main>
      </div>
    </div>
  );
}
